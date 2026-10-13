package com.fuelingchat.infra.llm

import ai.koog.http.client.ktor.KtorKoogHttpClient
import com.fuelingchat.config.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

// MockEngine tests for DeepSeekLlmClient (no network). Both tests capture every outgoing request
// (URL, Authorization header, body) inside the MockEngine handler lambda.
//
// Wire contract pinned from the Koog 1.3.0 jars at scaffold time (D-16):
//  - The default agent turn is NON-streaming: AIAgentLLMWriteSessionCommon.requestLLM ->
//    readSession.requestLLM -> PromptExecutor.executeProcessed -> AbstractOpenAILLMClient.execute
//    -> getResponse -> KoogHttpClient.post(path = "chat/completions", requestBody with stream = false).
//    So the MockEngine reply is a plain application/json chat.completion body, NOT an SSE stream
//    (the sse()/executeStreaming path is only taken by requestLLMStreaming, which KoogChatRunner
//    never calls).
//  - Outgoing request: POST https://api.deepseek.com/chat/completions (DeepSeekClientSettings
//    baseUrl default + chatCompletionsPath "chat/completions"); header Authorization: Bearer
//    <DEEPSEEK_API_KEY> (createConfiguredHttpClient -> Ktor defaultRequest); snake_case JSON body
//    with "model" = configured DEEPSEEK_MODEL, "stream" = false, "messages" and "tools".
//  - Reply envelope is ai.koog.prompt.executor.clients.deepseek.models.DeepSeekChatCompletionResponse,
//    whose REQUIRED (no-default) fields are: choices (OpenAIChoice: index, finish_reason, message),
//    created (Long), id (String), model (String), system_fingerprint (String). "object" defaults to
//    "chat.completion" and may be omitted. choices must be non-empty
//    (processProviderChatResponse requires it).
//  - Tool call reply: choices[0].finish_reason = "tool_calls", message = {role: "assistant",
//    tool_calls: [{id, type: "function", function: {name, arguments (JSON string)}}]}; content may
//    be absent (OpenAIMessage.Assistant.content is nullable).
//  - After the agent executes FakeEchoTool it sends a SECOND request whose last message is
//    role "tool" with content = FakeEchoTool.FIXED_RESULT and tool_call_id = the id from the reply.
class DeepSeekLlmClientTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun plainTextReplyYieldsFinalText() {
        val plainReply = "You got 28.5 litres of AI-95."
        val requests = mutableListOf<HttpRequestData>()
        val client =
            deepSeekClient { request ->
                requests += request
                respond(
                    plainTextReply(plainReply),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }

        val summary =
            runBlocking {
                client.run(
                    systemPrompt = SYSTEM_PROMPT,
                    history =
                        listOf(
                            ChatMessage(ChatMessage.ROLE_USER, HISTORY_USER_BODY, 1L),
                            ChatMessage(ChatMessage.ROLE_ASSISTANT, HISTORY_ASSISTANT_BODY, 2L),
                        ),
                    userMessage = USER_MESSAGE,
                    tools = listOf(FakeEchoTool()),
                )
            }

        assertEquals(plainReply, summary)
        assertEquals(1, requests.size)
        val request = requests.single()
        assertEquals("api.deepseek.com", request.url.host)
        assertEquals("/chat/completions", request.url.encodedPath)
        assertEquals("Bearer $API_KEY", request.headers[HttpHeaders.Authorization])
        val requestBody = requestBodyText(request)
        assertOutgoingTurn(requestBody, listOf("system", "user", "assistant", "user"))
        val messages = Json.parseToJsonElement(requestBody).jsonObject["messages"]!!.jsonArray
        assertEquals(
            listOf(SYSTEM_PROMPT, HISTORY_USER_BODY, HISTORY_ASSISTANT_BODY, USER_MESSAGE),
            messages.map { it.jsonObject["content"]!!.jsonPrimitive.content },
        )
    }

    @Test
    fun toolCallReplyDrivesAgentLoopAndReturnsFinalAnswer() {
        val requests = mutableListOf<HttpRequestData>()
        val client =
            deepSeekClient { request ->
                requests += request
                val reply = if (requests.size == 1) toolCallReply() else plainTextReply(FINAL_ANSWER)
                respond(reply, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }

        val summary =
            runBlocking {
                client.run(
                    systemPrompt = SYSTEM_PROMPT,
                    history = emptyList(),
                    userMessage = TOOL_USER_MESSAGE,
                    tools = listOf(FakeEchoTool()),
                )
            }

        assertEquals(FINAL_ANSWER, summary)
        assertEquals(2, requests.size)
        assertToolResultEchoedBack(requestBodyText(requests[1]))
    }

    private fun deepSeekClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): DeepSeekLlmClient =
        DeepSeekLlmClient(
            config = appConfig(),
            httpClientFactory =
                KtorKoogHttpClient.Factory(baseClient = HttpClient(MockEngine { request -> handler(request) })),
        )

    private fun requestBodyText(request: HttpRequestData): String =
        when (val body = request.body) {
            is OutgoingContent.ByteArrayContent -> body.bytes().decodeToString()
            else -> error("unexpected request body type: ${body::class.simpleName}")
        }

    private fun assertOutgoingTurn(
        requestBody: String,
        expectedRoles: List<String>,
    ) {
        val request = Json.parseToJsonElement(requestBody).jsonObject
        assertEquals(TEST_MODEL, request["model"]!!.jsonPrimitive.content)
        assertEquals(false, request["stream"]!!.jsonPrimitive.boolean)
        val messages = request["messages"]!!.jsonArray
        assertEquals(expectedRoles, messages.map { it.jsonObject["role"]!!.jsonPrimitive.content })
        val firstMessage = messages.first().jsonObject
        assertEquals(SYSTEM_PROMPT, firstMessage["content"]!!.jsonPrimitive.content)
        assertToolSchema(request["tools"]!!.jsonArray)
    }

    private fun assertToolSchema(tools: JsonArray) {
        val tool = tools.single().jsonObject
        assertEquals("function", tool["type"]!!.jsonPrimitive.content)
        val function = tool["function"]!!.jsonObject
        assertEquals(FakeEchoTool.NAME, function["name"]!!.jsonPrimitive.content)
        val parameters = function["parameters"]!!.jsonObject
        assertEquals("object", parameters["type"]!!.jsonPrimitive.content)
        val echoId = parameters["properties"]!!.jsonObject["echo_id"]!!.jsonObject
        assertEquals("string", echoId["type"]!!.jsonPrimitive.content)
        assertEquals(listOf("echo_id"), parameters["required"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    private fun assertToolResultEchoedBack(requestBody: String) {
        val request = Json.parseToJsonElement(requestBody).jsonObject
        val messages = request["messages"]!!.jsonArray
        val lastMessage = messages.last().jsonObject
        assertEquals("tool", lastMessage["role"]!!.jsonPrimitive.content)
        assertEquals(FakeEchoTool.FIXED_RESULT, lastMessage["content"]!!.jsonPrimitive.content)
        assertEquals(TOOL_CALL_ID, lastMessage["tool_call_id"]!!.jsonPrimitive.content)
    }

    private fun plainTextReply(text: String): String =
        buildJsonObject {
            put("id", "chatcmpl-test")
            put("object", "chat.completion")
            put("created", 1)
            put("model", TEST_MODEL)
            put("system_fingerprint", "fp-test")
            putJsonArray("choices") {
                addJsonObject {
                    put("index", 0)
                    put("finish_reason", "stop")
                    putJsonObject("message") {
                        put("role", "assistant")
                        put("content", text)
                    }
                }
            }
        }.toString()

    private fun toolCallReply(): String =
        buildJsonObject {
            put("id", "chatcmpl-test")
            put("object", "chat.completion")
            put("created", 1)
            put("model", TEST_MODEL)
            put("system_fingerprint", "fp-test")
            putJsonArray("choices") {
                addJsonObject {
                    put("index", 0)
                    put("finish_reason", "tool_calls")
                    putJsonObject("message") {
                        put("role", "assistant")
                        putJsonArray("tool_calls") {
                            addJsonObject {
                                put("id", TOOL_CALL_ID)
                                put("type", "function")
                                putJsonObject("function") {
                                    put("name", FakeEchoTool.NAME)
                                    put("arguments", """{"echo_id":"F-1"}""")
                                }
                            }
                        }
                    }
                }
            }
        }.toString()

    private fun appConfig(): AppConfig {
        val envFile = tempDir.resolve(".env")
        Files.writeString(
            envFile,
            """
            DB_PASSWORD=test-db
            DEEPSEEK_API_KEY=$API_KEY
            DEEPSEEK_MODEL=$TEST_MODEL
            OLLAMA_MODEL=test-ollama-model
            """.trimIndent(),
        )
        return AppConfig(envFile)
    }

    companion object {
        private const val TEST_MODEL = "test-deepseek-model"
        private const val API_KEY = "test-deepseek-key"
        private const val TOOL_CALL_ID = "call-test-1"
        private const val FINAL_ANSWER = "the final answer"
        private const val SYSTEM_PROMPT = "Answer fueling questions only via the get_fueling_info tool."
        private const val HISTORY_USER_BODY = "my last fueling was yesterday"
        private const val HISTORY_ASSISTANT_BODY = "Noted, ask me about it any time."
        private const val USER_MESSAGE = "how much fuel did I get"
        private const val TOOL_USER_MESSAGE = "look up F-1"
    }
}
