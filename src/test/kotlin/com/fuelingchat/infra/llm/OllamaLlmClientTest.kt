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
import kotlinx.serialization.Serializable
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
 * CONTRACT-LLM-001 MockEngine tests for the `local` client — no Ollama server runs.
 *
 * The MockEngine plays the Ollama native `<baseUrl>/api/chat` endpoint (verified Koog 1.3.0
 * wire format, D-16): outgoing request bodies carry model id + tools array
 * ({"type":"function","function":{"name","description","parameters"}}); plain-text replies carry
 * message.content, tool-call replies carry message.tool_calls[].function.{name,arguments} and
 * `done: true`; tool results go back as role `"tool"` messages in the follow-up request.
 *
 * Owner: coder-implementer, Block 3. Constructs Block 2's OllamaLlmClient(config, factory) and
 * exercises Block 1's runTurn through the pinned LlmClient seam.
 */
class OllamaLlmClientTest {
    // Block 3 — config from a temp env file (AppConfigTest pattern: @TempDir + Files.writeString):
    //   required keys DB_PASSWORD / DEEPSEEK_API_KEY / DEEPSEEK_MODEL, plus
    //   OLLAMA_BASE_URL=http://localhost:11434 and OLLAMA_MODEL=test-model.
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

    // Block 3 — the fake service-supplied tool (D-8): SimpleTool<FakeArgs> named "fake_tool",
    //   description "Returns a fixed string.", execute returns FAKE_TOOL_OUTPUT — proves the
    //   service-supplied tool set is actually wired into the Koog run:
    //   SimpleTool<FakeArgs>(argsType = typeToken<FakeArgs>(), name = "fake_tool",
    //   description = "Returns a fixed string.")
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
    }

    // Ollama native api/chat reply recipes (Koog 1.3.0 wire format, D-16): `done` is required and
    // `message.content` is non-null even when the message only carries tool calls.
    private val plainReplyJson: String =
        """{"model":"test-model","message":{"role":"assistant","content":"$PLAIN_ANSWER"},"done":true}"""

    private val finalReplyJson: String =
        """{"model":"test-model","message":{"role":"assistant","content":"$FINAL_ANSWER"},"done":true}"""

    private val toolCallReplyJson: String =
        """{"model":"test-model","message":{"role":"assistant","content":"","tool_calls":[""" +
            """{"function":{"name":"fake_tool","arguments":{"fueling_id":"F-10001"}}}]},"done":true}"""

    // Block 3 — MockEngine-backed factory (dep io.ktor:ktor-client-mock-jvm:3.3.3, added by the
    //   integrator in build.gradle.kts — do not add it here):
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

    // Block 3 — plain-text reply test (runBlocking): MockEngine answers every api/chat POST with
    //   {"model":"test-model","message":{"role":"assistant","content":PLAIN_ANSWER},"done":true}
    //   as application/json. Construct OllamaLlmClient(config, mockFactory) and assert
    //   run(systemPrompt, history = emptyList(), userMessage, tools = listOf(fakeTool)) == PLAIN_ANSWER.
    //   Also capture the outgoing request and assert: url path is "/api/chat";
    //   body.model == "test-model" (config.ollamaModel); body.tools[0].type == "function";
    //   body.tools[0].function.name == "fake_tool"; body.tools[0].function.parameters is a JSON
    //   Schema object (type "object" with properties and required).
    @Test
    fun plainTextReplyYieldsFinalText(): Unit =
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            val client =
                OllamaLlmClient(
                    config,
                    mockFactory { request ->
                        requests += request
                        jsonResponse(plainReplyJson)
                    },
                )

            assertEquals(PLAIN_ANSWER, runFakeTurn(client))
            assertToolSchema(assertChatRequest(requests.single()))
        }

    // Block 3 — tool-loop test (runBlocking): the MockEngine replies to the FIRST api/chat POST with
    //   {"model":"test-model","message":{"role":"assistant","content":"","tool_calls":
    //   [{"function":{"name":"fake_tool","arguments":{"fueling_id":"F-10001"}}}]},"done":true}
    //   and to the SECOND POST with a plain text FINAL_ANSWER reply. Assert
    //   run(systemPrompt, history, userMessage, listOf(fakeTool)) == FINAL_ANSWER; assert the second
    //   request body messages contains a role == "tool" entry with content == FAKE_TOOL_OUTPUT —
    //   proving the agent loop executed the fake tool and sent its fixed text back (D-8).
    @Test
    fun toolCallReplyDrivesAgentLoop(): Unit =
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            val replies = ArrayDeque(listOf(toolCallReplyJson, finalReplyJson))
            val client =
                OllamaLlmClient(
                    config,
                    mockFactory { request ->
                        requests += request
                        jsonResponse(replies.removeFirst())
                    },
                )

            assertEquals(FINAL_ANSWER, runFakeTurn(client))
            assertEquals(2, requests.size)
            val firstBody = assertChatRequest(requests[0])
            val secondBody = assertChatRequest(requests[1])
            assertToolSchema(firstBody)
            assertToolResultSentBack(secondBody)
        }

    private suspend fun runFakeTurn(client: LlmClient): String =
        client.run(
            systemPrompt = "system-prompt",
            history = emptyList(),
            userMessage = "user-message",
            tools = listOf(fakeTool),
        )

    private suspend fun assertChatRequest(request: HttpRequestData): JsonObject {
        assertEquals(config.ollamaBaseUrl, request.url.protocolWithAuthority)
        assertEquals("/api/chat", request.url.encodedPath)
        val body = parseBody(request)
        assertEquals(config.ollamaModel, body.getValue("model").jsonPrimitive.content)
        return body
    }

    private suspend fun parseBody(request: HttpRequestData): JsonObject =
        Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject

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

    private fun assertToolResultSentBack(body: JsonObject) {
        val messages = body.getValue("messages").jsonArray.map { it.jsonObject }
        val toolMessages = messages.filter { it["role"]?.jsonPrimitive?.content == "tool" }

        assertEquals(1, toolMessages.size, "expected one tool-result message, got $messages")
        assertEquals(
            FAKE_TOOL_OUTPUT,
            toolMessages
                .single()
                .getValue("content")
                .jsonPrimitive.content,
        )
    }

    private fun envFile(content: String): Path {
        val file = tempDir.resolve("ollama-llm-client-test.env")
        Files.writeString(file, content)
        return file
    }
}

// Block 3 — the fake tool's @Serializable args class (pattern: GetFuelingInfoArgs).
// The wire key stays `fueling_id` (tool schema + arguments); the Kotlin name would otherwise trip
// detekt's ConstructorParameterNaming, which detekt.yml excludes per-file for GetFuelingInfoArgs.
@Suppress("ConstructorParameterNaming")
@Serializable
data class FakeArgs(
    val fueling_id: String,
)
