package com.fuelingchat.infra.llm

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.Tool
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.serialization.typeToken
import com.fuelingchat.config.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
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
 * The `remote` DeepSeek seam (CONTRACT-LLM-002) against a `MockEngine`: no network, no DeepSeek account,
 * no DB and no service classes. The outgoing request is asserted at the wire level — the DeepSeek API
 * base, the Bearer auth and the configured model id — and the D-8 tool-set contract is proven by driving
 * the same fake-tool loop as T13's `local` test through a DeepSeek tool-call reply.
 */
class DeepSeekLlmClientTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun theOutgoingRequestTargetsTheDeepSeekApiBaseWithBearerAuthAndTheConfiguredModelId() {
        val config = config()
        val (client, requests) = clientOf(config, listOf(textCompletion()))

        val answer = runBlocking { client.run(SYSTEM_PROMPT, emptyList(), USER_MESSAGE, testTools()) }

        assertEquals(FINAL_TEXT, answer)
        val request = requests.single()
        assertEquals("https", request.scheme)
        assertEquals(BASE_HOST, request.host, "the outgoing request must target the DeepSeek API base")
        assertEquals(CHAT_COMPLETIONS_PATH, request.path)
        assertEquals(
            "Bearer $API_KEY",
            request.headers[HttpHeaders.Authorization],
            "the api_key must be sent as Bearer auth",
        )
        val model = request.bodyJson()["model"]!!.jsonPrimitive.content
        assertEquals(MODEL_ID, model, "the configured DEEPSEEK_MODEL must be sent")
        assertEquals(config.deepseekModel, model)
    }

    @Test
    fun aToolCallReplyDrivesTheFakeToolLoopAndTheServiceSuppliedToolSetReachesTheProvider() {
        val (client, requests) = clientOf(config(), listOf(toolCallCompletion(), textCompletion()))

        val answer = runBlocking { client.run(SYSTEM_PROMPT, emptyList(), USER_MESSAGE, testTools()) }

        assertEquals(FINAL_TEXT, answer, "the loop must return the final text of the second round-trip")
        assertEquals(2, requests.size, "the tool call must trigger exactly one more provider round-trip")
        assertToolSetCarriesTheFakeTool(requests.first())
        assertFakeToolOutputWentBack(requests.last())
    }

    /** D-8: the service-supplied tool set is registered into the run and serialized into the request. */
    private fun assertToolSetCarriesTheFakeTool(request: CapturedRequest) {
        val tools = request.bodyJson()["tools"]!!.jsonArray
        assertTrue(tools.isNotEmpty(), "the tool set sent to the remote provider is never empty")
        val names =
            tools.map {
                it.jsonObject["function"]!!
                    .jsonObject["name"]!!
                    .jsonPrimitive.content
            }
        assertEquals(listOf(TOOL_NAME), names)
    }

    /** The loop proof: the fake tool ran and its fixed output travelled back as the tool message. */
    private fun assertFakeToolOutputWentBack(request: CapturedRequest) {
        val toolMessages =
            request
                .bodyJson()["messages"]!!
                .jsonArray
                .map { it.jsonObject }
                .filter { it["role"]?.jsonPrimitive?.content == ROLE_TOOL }
        val message = toolMessages.single()
        assertEquals(TOOL_CALL_ID, message["tool_call_id"]!!.jsonPrimitive.content)
        assertEquals(FAKE_TOOL_OUTPUT, message["content"]!!.jsonPrimitive.content)
    }

    private fun clientOf(
        config: AppConfig,
        replies: List<String>,
    ): Pair<LlmClient, List<CapturedRequest>> {
        val captured = mutableListOf<CapturedRequest>()
        val engine =
            MockEngine { request ->
                val body = request.body.toByteArray().decodeToString()
                captured +=
                    CapturedRequest(
                        scheme = request.url.protocol.name,
                        host = request.url.host,
                        path = request.url.encodedPath,
                        headers = request.headers,
                        body = body,
                    )
                respond(
                    content = replies[minOf(captured.size - 1, replies.lastIndex)],
                    status = HttpStatusCode.OK,
                    headers = JSON_HEADERS,
                )
            }
        return DeepSeekLlmClient(config, KtorKoogHttpClient.Factory(HttpClient(engine))) to captured
    }

    private fun testTools(): List<Tool<*, *>> = listOf(FakeFuelingInfoTool(TOOL_NAME, FAKE_TOOL_OUTPUT))

    private fun config(): AppConfig {
        val file = tempDir.resolve("deepseek-test.env")
        Files.writeString(
            file,
            """
            DB_PASSWORD=test-db
            DEEPSEEK_API_KEY=$API_KEY
            DEEPSEEK_MODEL=$MODEL_ID
            OLLAMA_MODEL=test-model
            """.trimIndent(),
        )
        return AppConfig(file)
    }

    /** One captured wire request, kept as plain values so the assertions read like the request itself. */
    private class CapturedRequest(
        val scheme: String,
        val host: String,
        val path: String,
        val headers: Headers,
        val body: String,
    ) {
        fun bodyJson(): JsonObject = Json.parseToJsonElement(body).jsonObject
    }

    /** The fake for the D-8 service-supplied tool: a fixed output proves the agent loop executed it. */
    private class FakeFuelingInfoTool(
        name: String,
        private val output: String,
    ) : SimpleTool<FakeFuelingInfoArgs>(
            argsType = typeToken<FakeFuelingInfoArgs>(),
            name = name,
            description = "Looks up one fueling by its identifier.",
        ) {
        override suspend fun execute(args: FakeFuelingInfoArgs): String = output
    }

    @Serializable
    private data class FakeFuelingInfoArgs(
        @SerialName("fueling_id") val fuelingId: String,
    )

    private companion object {
        const val API_KEY = "test-deepseek-key"
        const val MODEL_ID = "deepseek-v4-flash"
        const val BASE_HOST = "api.deepseek.com"
        const val CHAT_COMPLETIONS_PATH = "/chat/completions"
        const val SYSTEM_PROMPT = "Answer fueling questions only by calling the get_fueling_info tool."
        const val USER_MESSAGE = "How much fuel did F-10001 take?"
        const val TOOL_NAME = "get_fueling_info"
        const val TOOL_CALL_ID = "call_fake_1"
        const val ROLE_TOOL = "tool"
        const val FAKE_TOOL_OUTPUT = """{"fueling_id":"F-10001","liters":30.0}"""
        const val FINAL_TEXT = "Fueling F-10001 used 30.0 liters."

        val JSON_HEADERS: Headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

        /** A DeepSeek chat-completions reply carrying only text, in the pinned client's wire format. */
        fun textCompletion(): String =
            """
            {
              "id": "chatcmpl-fake-text",
              "object": "chat.completion",
              "created": 1700000000,
              "model": "$MODEL_ID",
              "system_fingerprint": "fp_fake",
              "choices": [
                {
                  "index": 0,
                  "finish_reason": "stop",
                  "message": {"role": "assistant", "content": "$FINAL_TEXT"}
                }
              ]
            }
            """.trimIndent()

        /** A DeepSeek chat-completions reply requesting the service-supplied tool, by name. */
        fun toolCallCompletion(): String =
            """
            {
              "id": "chatcmpl-fake-tool-call",
              "object": "chat.completion",
              "created": 1700000000,
              "model": "$MODEL_ID",
              "system_fingerprint": "fp_fake",
              "choices": [
                {
                  "index": 0,
                  "finish_reason": "tool_calls",
                  "message": {
                    "role": "assistant",
                    "tool_calls": [
                      {
                        "id": "$TOOL_CALL_ID",
                        "type": "function",
                        "function": {"name": "$TOOL_NAME", "arguments": "{\"fueling_id\":\"F-10001\"}"}
                      }
                    ]
                  }
                }
              ]
            }
            """.trimIndent()
    }
}
