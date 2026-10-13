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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DeepSeekLlmClientTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun plainTextReplyYieldsFinalText() =
        runBlocking {
            val outgoing = mutableListOf<String>()
            val urls = mutableListOf<Url>()
            val authHeaders = mutableListOf<String?>()
            val client =
                deepSeekClient { request ->
                    urls += request.url
                    authHeaders += request.headers[HttpHeaders.Authorization]
                    outgoing += requestBodyText(request)
                    respond(plainTextReply("28.5 liters of AI-95"), HttpStatusCode.OK)
                }

            val summary =
                client.run(
                    systemPrompt = SYSTEM_PROMPT,
                    history =
                        listOf(
                            ChatMessage("user", "previous question", 1L),
                            ChatMessage("assistant", "previous answer", 1L),
                        ),
                    userMessage = "how much fuel did I get",
                    tools = listOf(FakeEchoTool()),
                )

            assertEquals("28.5 liters of AI-95", summary)
            assertEquals("api.deepseek.com", urls.single().host)
            assertEquals("/chat/completions", urls.single().encodedPath)
            assertEquals("Bearer $API_KEY", authHeaders.single())
            assertOutgoingTurn(outgoing.single(), listOf("system", "user", "assistant", "user"))
            val messages =
                Json
                    .parseToJsonElement(outgoing.single())
                    .jsonObject
                    .getValue("messages")
                    .jsonArray
            assertEquals(
                "previous question",
                messages[1]
                    .jsonObject
                    .getValue("content")
                    .jsonPrimitive.content,
            )
            assertEquals(
                "how much fuel did I get",
                messages[3]
                    .jsonObject
                    .getValue("content")
                    .jsonPrimitive.content,
            )
        }

    @Test
    fun toolCallReplyDrivesAgentLoopAndReturnsFinalAnswer() =
        runBlocking {
            val outgoing = mutableListOf<String>()
            val client =
                deepSeekClient { request ->
                    outgoing += requestBodyText(request)
                    val reply = if (outgoing.size == 1) toolCallReply() else plainTextReply(FINAL_ANSWER)
                    respond(reply, HttpStatusCode.OK)
                }

            val summary =
                client.run(
                    systemPrompt = SYSTEM_PROMPT,
                    history = emptyList(),
                    userMessage = "look up F-1",
                    tools = listOf(FakeEchoTool()),
                )

            assertEquals(FINAL_ANSWER, summary)
            assertEquals(2, outgoing.size)
            assertOutgoingTurn(outgoing.first(), listOf("system", "user"))
            assertToolResultEchoedBack(outgoing.last())
        }

    private fun assertOutgoingTurn(
        requestBody: String,
        expectedRoles: List<String>,
    ) {
        val request = Json.parseToJsonElement(requestBody).jsonObject
        assertEquals(TEST_MODEL, request.getValue("model").jsonPrimitive.content)
        assertFalse(
            request
                .getValue("stream")
                .jsonPrimitive.content
                .toBoolean(),
        )
        val messages = request.getValue("messages").jsonArray
        assertEquals(
            expectedRoles,
            messages.map {
                it.jsonObject
                    .getValue("role")
                    .jsonPrimitive.content
            },
        )
        assertEquals(
            SYSTEM_PROMPT,
            messages
                .first()
                .jsonObject
                .getValue("content")
                .jsonPrimitive.content,
        )
        assertToolSchema(request.getValue("tools").jsonArray)
    }

    private fun assertToolSchema(tools: JsonArray) {
        val tool = tools.single().jsonObject
        assertEquals("function", tool.getValue("type").jsonPrimitive.content)
        val function = tool.getValue("function").jsonObject
        assertEquals(FakeEchoTool.NAME, function.getValue("name").jsonPrimitive.content)
        val parameters = function.getValue("parameters").jsonObject
        assertEquals("object", parameters.getValue("type").jsonPrimitive.content)
        val echoIdSchema =
            parameters
                .getValue("properties")
                .jsonObject
                .getValue("echo_id")
                .jsonObject
        assertEquals("string", echoIdSchema.getValue("type").jsonPrimitive.content)
        assertEquals(
            listOf("echo_id"),
            parameters.getValue("required").jsonArray.map { it.jsonPrimitive.content },
        )
    }

    private fun assertToolResultEchoedBack(requestBody: String) {
        val messages =
            Json
                .parseToJsonElement(requestBody)
                .jsonObject
                .getValue("messages")
                .jsonArray
        val toolMessage = messages.last().jsonObject
        assertEquals("tool", toolMessage.getValue("role").jsonPrimitive.content)
        assertEquals(FakeEchoTool.FIXED_RESULT, toolMessage.getValue("content").jsonPrimitive.content)
        assertEquals(TOOL_CALL_ID, toolMessage.getValue("tool_call_id").jsonPrimitive.content)
    }

    private fun deepSeekClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): DeepSeekLlmClient {
        val factory =
            KtorKoogHttpClient.Factory(
                baseClient =
                    HttpClient(
                        MockEngine { request ->
                            handler(request)
                        },
                    ),
            )
        return DeepSeekLlmClient(appConfig(), factory)
    }

    private fun requestBodyText(request: HttpRequestData): String =
        (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    private fun plainTextReply(text: String): String =
        Json.encodeToString(
            buildJsonObject {
                put("id", "chatcmpl-test")
                put("object", "chat.completion")
                put("created", 1L)
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
            },
        )

    private fun toolCallReply(): String =
        Json.encodeToString(
            buildJsonObject {
                put("id", "chatcmpl-tool")
                put("object", "chat.completion")
                put("created", 1L)
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
            },
        )

    private fun appConfig(): AppConfig {
        val file = tempDir.resolve("llm-test-${System.nanoTime()}.env")
        Files.writeString(
            file,
            """
            DB_PASSWORD=test-db
            DEEPSEEK_API_KEY=$API_KEY
            DEEPSEEK_MODEL=$TEST_MODEL
            OLLAMA_MODEL=test-ollama-model
            """.trimIndent(),
        )
        return AppConfig(file)
    }

    companion object {
        private const val TEST_MODEL = "test-deepseek-model"
        private const val API_KEY = "test-deepseek-key"
        private const val TOOL_CALL_ID = "call-test-1"
        private const val FINAL_ANSWER = "the final answer"
        private const val SYSTEM_PROMPT = "Answer fueling questions only via the get_fueling_info tool."
    }
}
