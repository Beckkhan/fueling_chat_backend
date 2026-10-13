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
import io.ktor.http.Url
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

// MockEngine tests for OllamaLlmClient (no Ollama running). Both tests capture every outgoing
// request (URL + body) inside the MockEngine handler lambda.
class OllamaLlmClientTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun plainTextReplyYieldsFinalText() {
        val requests = mutableListOf<HttpRequestData>()
        val client =
            ollamaClient { request ->
                requests += request
                respond(plainTextReply(PLAIN_REPLY), HttpStatusCode.OK, JSON_HEADERS)
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

        assertEquals(PLAIN_REPLY, summary)
        assertEquals(1, requests.size)
        assertEquals(Url("$OLLAMA_BASE_URL/api/chat"), requests.single().url)
        val turn = Json.parseToJsonElement(requestBodyText(requests.single())).jsonObject
        assertOutgoingTurn(turn.toString(), listOf("system", "user", "assistant", "user"))
        assertEquals(
            listOf(SYSTEM_PROMPT, HISTORY_USER_BODY, HISTORY_ASSISTANT_BODY, USER_MESSAGE),
            turn["messages"]!!.jsonArray.map { it.jsonObject["content"]!!.jsonPrimitive.content },
        )
    }

    @Test
    fun toolCallReplyDrivesAgentLoopAndReturnsFinalAnswer() {
        val requests = mutableListOf<HttpRequestData>()
        val client =
            ollamaClient { request ->
                requests += request
                val reply = if (requests.size == 1) toolCallReply() else plainTextReply(FINAL_ANSWER)
                respond(reply, HttpStatusCode.OK, JSON_HEADERS)
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

    private fun ollamaClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): OllamaLlmClient =
        OllamaLlmClient(
            config = appConfig(),
            httpClientFactory =
                KtorKoogHttpClient.Factory(baseClient = HttpClient(MockEngine { request -> handler(request) })),
        )

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
        val echoId = parameters["properties"]!!.jsonObject[ECHO_ID_PROPERTY]!!.jsonObject
        assertEquals("string", echoId["type"]!!.jsonPrimitive.content)
        assertEquals(listOf(ECHO_ID_PROPERTY), parameters["required"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    private fun assertToolResultEchoedBack(requestBody: String) {
        val request = Json.parseToJsonElement(requestBody).jsonObject
        val messages = request["messages"]!!.jsonArray
        val lastMessage = messages.last().jsonObject
        assertEquals("tool", lastMessage["role"]!!.jsonPrimitive.content)
        assertEquals(FakeEchoTool.FIXED_RESULT, lastMessage["content"]!!.jsonPrimitive.content)
    }

    private fun requestBodyText(request: HttpRequestData): String =
        when (val body = request.body) {
            is OutgoingContent.ByteArrayContent -> body.bytes().decodeToString()
            else -> error("unexpected request body type: ${body::class.simpleName}")
        }

    private fun plainTextReply(text: String): String =
        buildJsonObject {
            put("model", TEST_MODEL)
            putJsonObject("message") {
                put("role", "assistant")
                put("content", text)
            }
            put("done", true)
        }.toString()

    private fun toolCallReply(): String =
        buildJsonObject {
            put("model", TEST_MODEL)
            putJsonObject("message") {
                put("role", "assistant")
                put("content", "")
                putJsonArray("tool_calls") {
                    addJsonObject {
                        putJsonObject("function") {
                            put("name", FakeEchoTool.NAME)
                            putJsonObject("arguments") {
                                put(ECHO_ID_PROPERTY, TOOL_CALL_ECHO_ID)
                            }
                        }
                    }
                }
            }
            put("done", true)
        }.toString()

    private fun appConfig(): AppConfig {
        val envFile = tempDir.resolve(".env")
        Files.writeString(
            envFile,
            """
            DB_PASSWORD=test-db
            DEEPSEEK_API_KEY=test-key
            DEEPSEEK_MODEL=test-model
            OLLAMA_BASE_URL=$OLLAMA_BASE_URL
            OLLAMA_MODEL=$TEST_MODEL
            """.trimIndent(),
        )
        return AppConfig(envFile)
    }

    companion object {
        private const val TEST_MODEL = "test-ollama-model"
        private const val FINAL_ANSWER = "the final answer"
        private const val SYSTEM_PROMPT = "Answer fueling questions only via the get_fueling_info tool."
        private const val OLLAMA_BASE_URL = "http://ollama.test:11434"
        private const val PLAIN_REPLY = "You got 28.5 litres of AI-95."
        private const val HISTORY_USER_BODY = "my last fueling was yesterday"
        private const val HISTORY_ASSISTANT_BODY = "Noted, ask me about it any time."
        private const val USER_MESSAGE = "how much fuel did I get"
        private const val TOOL_USER_MESSAGE = "look up F-1"
        private const val ECHO_ID_PROPERTY = "echo_id"
        private const val TOOL_CALL_ECHO_ID = "F-1"
        private val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
