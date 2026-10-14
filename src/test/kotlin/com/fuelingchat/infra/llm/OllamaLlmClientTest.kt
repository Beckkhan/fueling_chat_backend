package com.fuelingchat.infra.llm

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.serialization.typeToken
import com.fuelingchat.config.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** A model id and a base URL that no production default uses, so a hardcoded endpoint cannot pass. */
private const val OLLAMA_MODEL = "llama3.2-local-test"
private const val OLLAMA_BASE_URL = "http://127.0.0.1:19191/v1"

private const val FUELING_ID = "F-10001"
private const val TOOL_RESULT = "fake tool payload for $FUELING_ID"
private const val FINAL_TEXT = "fueling $FUELING_ID cost 91.20 EUR"
private const val SYSTEM_PROMPT = "You answer fueling questions."
private const val USER_MESSAGE = "What did fueling $FUELING_ID cost?"

/**
 * `OllamaLlmClient` (D-8, D-16) against a MockEngine-backed Koog HTTP-client factory: no network, no
 * Ollama process, no DB. The tool set arrives exactly as the service supplies it, so the tool-call test
 * proves the injected registry is wired into the agent run.
 */
class OllamaLlmClientTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun plainTextReplyIsReturnedAsTheFinalText() =
        runBlocking {
            val harness = harness(listOf(assistantText(FINAL_TEXT)))

            val result = harness.client.run(SYSTEM_PROMPT, emptyList(), USER_MESSAGE, listOf(harness.tool))

            assertEquals(FINAL_TEXT, result)
            assertEquals(1, harness.requests.size, "a plain reply needs exactly one model call")
            assertEquals(0, harness.tool.invocations, "no tool call was requested, so no tool may run")
        }

    @Test
    fun toolCallReplyRunsTheSuppliedToolThroughTheAgentLoopAndReturnsTheFinalAnswer() =
        runBlocking {
            val harness = harness(listOf(assistantToolCall(), assistantText(FINAL_TEXT)))

            val result = harness.client.run(SYSTEM_PROMPT, emptyList(), USER_MESSAGE, listOf(harness.tool))

            assertEquals(FINAL_TEXT, result)
            assertEquals(1, harness.tool.invocations, "the service-supplied tool must be wired into the run")
            assertEquals(FUELING_ID, harness.tool.lastFuelingId, "the model-supplied argument must reach the tool")
            assertEquals(2, harness.requests.size, "the loop must feed the tool result back to the model")
            assertTrue(
                harness.requests[1].body.contains(TOOL_RESULT),
                "the tool result must be sent back to the model: ${harness.requests[1].body}",
            )
        }

    @Test
    fun outgoingRequestUsesTheConfiguredModelAndEndpointAndCarriesTheToolSchema() =
        runBlocking {
            val harness = harness(listOf(assistantText(FINAL_TEXT)))

            harness.client.run(SYSTEM_PROMPT, emptyList(), USER_MESSAGE, listOf(harness.tool))

            val request = harness.requests.single()
            assertEquals(HttpMethod.Post, request.method)
            assertTrue(
                request.url.startsWith(harness.config.ollamaBaseUrl),
                "the endpoint must come from config.ollamaBaseUrl: ${request.url}",
            )
            assertTrue(request.url.endsWith("/api/chat"), "the pinned Ollama chat endpoint: ${request.url}")
            val body = Json.parseToJsonElement(request.body).jsonObject
            assertEquals(harness.config.ollamaModel, body.string("model"))
            assertFalse(body["stream"]!!.jsonPrimitive.boolean, "D-7: no streaming request may be sent")
            assertToolSchema(body)
        }

    private fun harness(responses: List<String>): Harness {
        val config =
            AppConfig(
                envFile =
                    Files.writeString(
                        tempDir.resolve("ollama-test.env"),
                        """
                        DB_PASSWORD=test-db
                        DEEPSEEK_API_KEY=test-deepseek-key
                        DEEPSEEK_MODEL=test-deepseek-model
                        OLLAMA_MODEL=$OLLAMA_MODEL
                        OLLAMA_BASE_URL=$OLLAMA_BASE_URL
                        """.trimIndent(),
                    ),
            )
        return Harness(config, responses)
    }
}

/** Drives the client over a MockEngine that answers in order and records every outgoing request. */
private class Harness(
    val config: AppConfig,
    private val responses: List<String>,
) {
    val tool = FakeFuelingInfoTool()
    val requests = mutableListOf<RecordedRequest>()
    val client: OllamaLlmClient

    init {
        val engine =
            MockEngine { request ->
                require(requests.size < responses.size) { "unexpected model call #${requests.size + 1}" }
                requests += RecordedRequest(request.url.toString(), request.method, (request.body as TextContent).text)
                respond(
                    content = responses[requests.size - 1],
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        client = OllamaLlmClient(config, KtorKoogHttpClient.Factory(baseClient = HttpClient(engine), withSse = false))
    }
}

private data class RecordedRequest(
    val url: String,
    val method: HttpMethod,
    val body: String,
)

/** The service supplies the real tool; its fixed-text twin keeps the DB and the tool loop out of this test. */
private class FakeFuelingInfoTool :
    SimpleTool<FakeFuelingInfoArgs>(
        argsType = typeToken<FakeFuelingInfoArgs>(),
        name = "get_fueling_info",
        description = "Look up one fueling by its id.",
    ) {
    var invocations = 0
    var lastFuelingId: String? = null

    override suspend fun execute(args: FakeFuelingInfoArgs): String {
        invocations++
        lastFuelingId = args.fuelingId
        return TOOL_RESULT
    }
}

@Serializable
private data class FakeFuelingInfoArgs(
    @property:LLMDescription("The fueling identifier to look up.")
    @SerialName("fueling_id")
    val fuelingId: String,
)

/**
 * CONTRACT-TOOL-001 names the tool's JSON schema `input_schema`; Koog's Ollama wire DTO carries the same
 * schema under `function.parameters`, so this asserts the wire shape of the descriptor-derived schema.
 */
private fun assertToolSchema(body: JsonObject) {
    val tool = body["tools"]!!.jsonArray.single()
    val function = tool.jsonObject["function"]!!.jsonObject
    assertEquals("get_fueling_info", function.string("name"))
    val schema = function["parameters"]!!.jsonObject
    assertEquals("object", schema.string("type"))
    val properties = schema["properties"]!!.jsonObject
    assertEquals("string", properties["fueling_id"]!!.jsonObject.string("type"))
    val required = schema["required"]!!.jsonArray.map { it.jsonPrimitive.content }
    assertTrue(required.contains("fueling_id"), "fueling_id must be required: $schema")
}

private fun JsonObject.string(key: String): String = this[key]!!.jsonPrimitive.content

private fun assistantText(text: String): String =
    """{"model":"$OLLAMA_MODEL","message":{"role":"assistant","content":"$text"},"done":true}"""

private fun assistantToolCall(): String =
    """{"model":"$OLLAMA_MODEL","message":{"role":"assistant","content":"","tool_calls":""" +
        """[{"function":{"name":"get_fueling_info","arguments":{"fueling_id":"$FUELING_ID"}}}]},"done":true}"""
