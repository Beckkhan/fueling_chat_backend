package com.fuelingchat.infra.llm

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.Tool
import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.serialization.typeToken
import com.fuelingchat.config.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.protocolWithAuthority
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * CONTRACT-LLM-002 MockEngine tests for the `remote` client — no network, no DeepSeek account.
 *
 * The MockEngine plays the DeepSeek OpenAI-compatible `<base>/chat/completions` endpoint
 * (verified against the pinned jars + the 1.3.0-beta deepseek module sources, D-16): outgoing
 * requests carry `Authorization: Bearer <api_key>` and the OpenAI wire format
 * ({"type":"function","function":{"name","description","parameters"}}); replies are
 * OpenAI-compatible with the `system_fingerprint` field REQUIRED (non-null decode field) and
 * tool-call `function.arguments` as a JSON string; tool results go back as role `"tool"` messages.
 *
 * The tool-loop test passes a NON-EMPTY history so KoogChatRunner's history role-mapping
 * (user/assistant) is covered — the path T13's test gate noted was never exercised.
 *
 * Owner: coder-implementer, Block 2. Constructs Block 1's DeepSeekLlmClient(config, factory),
 * reuses T13's @Serializable FakeArgs (same package — do NOT redeclare) and exercises T13's
 * KoogChatRunner.runTurn through the pinned LlmClient seam.
 */
class DeepSeekLlmClientTest {
    // Block 2 — config from a temp env file (AppConfigTest pattern: @TempDir + Files.writeString):
    //   required keys DB_PASSWORD / DEEPSEEK_API_KEY / DEEPSEEK_MODEL / OLLAMA_MODEL, plus
    //   OLLAMA_BASE_URL=http://localhost:11434. Use DEEPSEEK_API_KEY=test-deepseek-key and
    //   DEEPSEEK_MODEL=deepseek-test-model — the request/auth assertions pin against them.
    @TempDir
    lateinit var tempDir: Path

    // by lazy: JUnit injects @TempDir after construction, so the env file is written on first
    // access inside a test body — a real .env is never read.
    private val config: AppConfig by lazy {
        AppConfig(
            envFile(
                """
                DB_PASSWORD=test-db-password
                DEEPSEEK_API_KEY=test-deepseek-key
                DEEPSEEK_MODEL=deepseek-test-model
                OLLAMA_BASE_URL=http://localhost:11434
                OLLAMA_MODEL=test-model
                """.trimIndent(),
            ),
        )
    }

    // Block 2 — the fake service-supplied tool (D-8), reusing the @Serializable FakeArgs declared
    //   at the top level of T13's OllamaLlmClientTest.kt (same package — do NOT redeclare it): a
    //   SimpleTool<FakeArgs> named "fake_tool", description "Returns a fixed string.", execute
    //   returns FAKE_TOOL_OUTPUT — proves the service-supplied tool set is actually wired into
    //   the Koog run (identical shape to T13's fake tool).
    private val fakeTool: Tool<*, *> =
        object : SimpleTool<FakeArgs>(
            argsType = typeToken<FakeArgs>(),
            name = "fake_tool",
            description = "Returns a fixed string.",
        ) {
            override suspend fun execute(args: FakeArgs): String = FAKE_TOOL_OUTPUT
        }

    companion object {
        const val FAKE_TOOL_OUTPUT = "fixed-tool-text"
        const val PLAIN_ANSWER = "plain-answer-text"
        const val FINAL_ANSWER = "final-answer-text"
        const val HISTORY_USER_TEXT = "prior-user-text"
        const val HISTORY_ASSISTANT_TEXT = "prior-assistant-text"
    }

    // Verified DeepSeek OpenAI-compatible reply recipes (Koog 1.3.0-beta DeepSeekLLMClient decode,
    // Json { namingStrategy = SnakeCase; ignoreUnknownKeys = true }): `system_fingerprint` is a
    // NON-NULL String field of DeepSeekChatCompletionResponse — every reply must carry it or
    // decode throws; tool-call `function.arguments` is a JSON STRING on the wire.
    private val plainReplyJson: String =
        """
        {"id":"1","object":"chat.completion","created":123,"model":"deepseek-test-model",
        "system_fingerprint":"","choices":[{"index":0,"message":{"role":"assistant",
        "content":"$PLAIN_ANSWER"},"finish_reason":"stop"}]}
        """.trimIndent()

    private val finalReplyJson: String =
        """
        {"id":"3","object":"chat.completion","created":125,"model":"deepseek-test-model",
        "system_fingerprint":"","choices":[{"index":0,"message":{"role":"assistant",
        "content":"$FINAL_ANSWER"},"finish_reason":"stop"}]}
        """.trimIndent()

    private val toolCallReplyJson: String =
        """
        {"id":"2","object":"chat.completion","created":124,"model":"deepseek-test-model",
        "system_fingerprint":"","choices":[{"index":0,"message":{"role":"assistant","content":null,
        "tool_calls":[{"id":"call_1","type":"function",
        "function":{"name":"fake_tool","arguments":"{\"fueling_id\":\"F-10001\"}"}}]},
        "finish_reason":"tool_calls"}]}
        """.trimIndent()

    // Block 2 — MockEngine-backed factory (testImplementation io.ktor:ktor-client-mock-jvm:3.3.3,
    //   pre-staged in build.gradle.kts since T13 — do not add it here):
    //   KtorKoogHttpClient.Factory(HttpClient(MockEngine { request -> onRequest(request) }))
    private fun mockFactory(onRequest: suspend (HttpRequestData) -> HttpResponseData): KoogHttpClient.Factory =
        KtorKoogHttpClient.Factory(
            HttpClient(MockEngine { request -> onRequest(request) }),
        )

    // Ktor completes the job in a response's call context when it cleans the exchange up, so every
    // mocked response is built in a scope whose context carries a real Job.
    private fun jsonResponse(body: String): HttpResponseData =
        MockRequestHandleScope(Job()).respond(
            content = body,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )

    // Block 2 — outgoing-request assertions (DeepSeek wire surface, verified at scaffold time):
    //   url.protocolWithAuthority == "https://api.deepseek.com" (DeepSeekClientSettings default
    //   base — no AppConfig base-URL key); url.encodedPath == "/chat/completions";
    //   request.headers[HttpHeaders.Authorization] == "Bearer test-deepseek-key";
    //   body.model == config.deepseekModel.
    private suspend fun assertChatRequest(request: HttpRequestData): JsonObject {
        assertEquals("https://api.deepseek.com", request.url.protocolWithAuthority)
        assertEquals("/chat/completions", request.url.encodedPath)
        assertEquals("Bearer ${config.deepseekApiKey}", request.headers[HttpHeaders.Authorization])
        val body = parseBody(request)
        assertEquals(config.deepseekModel, body.getValue("model").jsonPrimitive.content)
        return body
    }

    private suspend fun parseBody(request: HttpRequestData): JsonObject =
        Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject

    // Block 2 — tool wire schema (D-8, same as T13): tools[0].type == "function";
    //   tools[0].function.name == "fake_tool"; description == "Returns a fixed string.";
    //   parameters is a JSON Schema object (type "object", properties.fueling_id present,
    //   required == ["fueling_id"]).
    private fun assertToolSchema(body: JsonObject) {
        val tool =
            body
                .getValue("tools")
                .jsonArray
                .single()
                .jsonObject
        assertEquals("function", tool.getValue("type").jsonPrimitive.content)
        val function = tool.getValue("function").jsonObject
        assertEquals("fake_tool", function.getValue("name").jsonPrimitive.content)
        assertEquals("Returns a fixed string.", function.getValue("description").jsonPrimitive.content)
        val parameters = function.getValue("parameters").jsonObject
        assertEquals("object", parameters.getValue("type").jsonPrimitive.content)
        assertTrue(parameters.getValue("properties").jsonObject.containsKey("fueling_id"))
        assertEquals(
            listOf("fueling_id"),
            parameters.getValue("required").jsonArray.map { it.jsonPrimitive.content },
        )
    }

    // Block 2 — agent-loop proof (D-8): exactly one role == "tool" message in the follow-up
    //   request whose content == FAKE_TOOL_OUTPUT.
    private fun assertToolResultSentBack(body: JsonObject) {
        val messages = body.getValue("messages").jsonArray.map { it.jsonObject }
        val toolMessages = messages.filter { it["role"]?.jsonPrimitive?.content == "tool" }

        assertEquals(1, toolMessages.size, "expected one tool-result message, got $messages")
        assertEquals(
            FAKE_TOOL_OUTPUT,
            toolMessages
                .single()
                .getValue("content")
                .jsonPrimitive
                .content,
        )
    }

    // Block 2 — history role-mapping proof (the path T13's gate noted was uncovered): the first
    //   request body messages must contain a role == "user" entry with content ==
    //   HISTORY_USER_TEXT and a role == "assistant" entry with content == HISTORY_ASSISTANT_TEXT.
    private fun assertHistoryMappedByRole(body: JsonObject) {
        val messages = body.getValue("messages").jsonArray.map { it.jsonObject }
        val contents =
            messages.groupBy(
                keySelector = { it["role"]?.jsonPrimitive?.content.orEmpty() },
                valueTransform = { it["content"]?.jsonPrimitive?.content.orEmpty() },
            )

        assertTrue(
            contents["system"]?.contains("system-prompt") == true,
            "system prompt missing: $messages",
        )
        assertTrue(
            contents["user"]?.contains(HISTORY_USER_TEXT) == true,
            "user history missing: $messages",
        )
        assertTrue(
            contents["assistant"]?.contains(HISTORY_ASSISTANT_TEXT) == true,
            "assistant history missing: $messages",
        )
    }

    private suspend fun runFakeTurn(
        client: LlmClient,
        history: List<ChatMessage>,
    ): String =
        client.run(
            systemPrompt = "system-prompt",
            history = history,
            userMessage = "user-message",
            tools = listOf(fakeTool),
        )

    private fun envFile(content: String): Path {
        val file = tempDir.resolve("deepseek-llm-client-test.env")
        Files.writeString(file, content)
        return file
    }

    // Block 2 — plain-text reply test (runBlocking): MockEngine answers every chat/completions
    //   POST with plainReplyJson as application/json. Construct
    //   DeepSeekLlmClient(config, mockFactory { request -> requests += request; jsonResponse(plainReplyJson) }),
    //   assert runFakeTurn(client, emptyList()) == PLAIN_ANSWER, then assertChatRequest(requests.single())
    //   and assertToolSchema on its body.
    @Test
    fun plainTextReplyYieldsFinalText(): Unit =
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            val client =
                DeepSeekLlmClient(
                    config,
                    mockFactory { request ->
                        requests += request
                        jsonResponse(plainReplyJson)
                    },
                )

            assertEquals(PLAIN_ANSWER, runFakeTurn(client, emptyList()))
            assertToolSchema(assertChatRequest(requests.single()))
        }

    // Block 2 — tool-loop test with NON-EMPTY history (runBlocking): history =
    //   listOf(ChatMessage(ChatMessage.ROLE_USER, HISTORY_USER_TEXT, 1L),
    //          ChatMessage(ChatMessage.ROLE_ASSISTANT, HISTORY_ASSISTANT_TEXT, 2L))
    //   so KoogChatRunner's history role-mapping is exercised. The MockEngine replies to the FIRST
    //   POST with toolCallReplyJson and to the SECOND with finalReplyJson (ArrayDeque). Assert
    //   runFakeTurn(client, history) == FINAL_ANSWER; assertEquals(2, requests.size);
    //   assertHistoryMappedByRole(first body) and assertToolSchema(first body);
    //   assertToolResultSentBack(second body).
    @Test
    fun toolCallReplyDrivesAgentLoopWithHistory(): Unit =
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            val replies = ArrayDeque(listOf(toolCallReplyJson, finalReplyJson))
            val client =
                DeepSeekLlmClient(
                    config,
                    mockFactory { request ->
                        requests += request
                        jsonResponse(replies.removeFirst())
                    },
                )
            val history =
                listOf(
                    ChatMessage(ChatMessage.ROLE_USER, HISTORY_USER_TEXT, 1L),
                    ChatMessage(ChatMessage.ROLE_ASSISTANT, HISTORY_ASSISTANT_TEXT, 2L),
                )

            assertEquals(FINAL_ANSWER, runFakeTurn(client, history))
            assertEquals(2, requests.size)
            val firstBody = assertChatRequest(requests[0])
            val secondBody = assertChatRequest(requests[1])
            assertHistoryMappedByRole(firstBody)
            assertToolSchema(firstBody)
            assertToolResultSentBack(secondBody)
        }
}
