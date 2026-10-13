package com.fuelingchat.infra.llm

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.serialization.typeToken
import com.fuelingchat.config.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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

class OllamaLlmClientTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun plainTextReplyYieldsFinalText() =
        runBlocking {
            val outgoing = mutableListOf<String>()
            val urls = mutableListOf<Url>()
            val client =
                ollamaClient { request ->
                    urls += request.url
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
            assertEquals("ollama.test", urls.single().host)
            assertEquals("/api/chat", urls.single().encodedPath)
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
                ollamaClient { request ->
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
    }

    private fun ollamaClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): OllamaLlmClient {
        val factory =
            KtorKoogHttpClient.Factory(
                baseClient =
                    HttpClient(
                        MockEngine { request ->
                            handler(request)
                        },
                    ),
            )
        return OllamaLlmClient(appConfig(), factory)
    }

    private fun requestBodyText(request: HttpRequestData): String =
        (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    private fun plainTextReply(text: String): String =
        Json.encodeToString(
            buildJsonObject {
                put("model", TEST_MODEL)
                putJsonObject("message") {
                    put("role", "assistant")
                    put("content", text)
                }
                put("done", true)
            },
        )

    private fun toolCallReply(): String =
        Json.encodeToString(
            buildJsonObject {
                put("model", TEST_MODEL)
                putJsonObject("message") {
                    put("role", "assistant")
                    put("content", "")
                    putJsonArray("tool_calls") {
                        addJsonObject {
                            putJsonObject("function") {
                                put("name", FakeEchoTool.NAME)
                                putJsonObject("arguments") { put("echo_id", "F-1") }
                            }
                        }
                    }
                }
                put("done", true)
            },
        )

    private fun appConfig(): AppConfig {
        val file = tempDir.resolve("llm-test-${System.nanoTime()}.env")
        Files.writeString(
            file,
            """
            DB_PASSWORD=test-db
            DEEPSEEK_API_KEY=test-key
            DEEPSEEK_MODEL=test-model
            OLLAMA_BASE_URL=http://ollama.test:11434
            OLLAMA_MODEL=$TEST_MODEL
            """.trimIndent(),
        )
        return AppConfig(file)
    }

    companion object {
        private const val TEST_MODEL = "test-ollama-model"
        private const val FINAL_ANSWER = "the final answer"
        private const val SYSTEM_PROMPT = "Answer fueling questions only via the get_fueling_info tool."
    }
}

@Serializable
data class FakeEchoArgs(
    @property:LLMDescription("The id to echo.")
    @SerialName("echo_id")
    val echoId: String,
)

class FakeEchoTool :
    SimpleTool<FakeEchoArgs>(
        argsType = typeToken<FakeEchoArgs>(),
        name = NAME,
        description = "Fake tool returning fixed text.",
    ) {
    override suspend fun execute(args: FakeEchoArgs): String = FIXED_RESULT

    companion object {
        const val NAME = "fake_echo"
        const val FIXED_RESULT = "fake tool fixed text"
    }
}
