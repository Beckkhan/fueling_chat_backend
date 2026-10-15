package com.fuelingchat.infra.llm

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fuelingchat.config.AppConfig
import com.fuelingchat.infra.logging.installRequestLogging
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

private const val OLLAMA_MODEL = "llama3.2-local-test"
private const val OLLAMA_BASE_URL = "http://127.0.0.1:19191/v1"
private const val DEEPSEEK_MODEL = "deepseek-v4-flash"
private const val DEEPSEEK_API_KEY = "test-deepseek-key"
private const val SYSTEM_PROMPT = "You answer fueling questions."
private const val USER_MESSAGE = "What did fueling F-10001 cost?"
private const val FINAL_TEXT = "fueling F-10001 cost 91.20 EUR"

/**
 * Pins the [LlmWireLogging] seam end to end: every provider round-trip emits one `llm request` line and
 * one `llm response` line, each carrying the complete wire JSON, the provider/model metadata and — when
 * the turn runs inside a chat request — the request correlation id. Secret-shaped fields in the logged
 * JSON stay masked by the production MaskingTurboFilter.
 */
class LlmWireLoggingTest {
    @TempDir
    lateinit var tempDir: Path

    private val logger: KLogger = KotlinLogging.logger("com.fuelingchat.infra.llm.LlmWireLoggingTest")
    private val appender = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun attachAppender() {
        appender.start()
        rootLogger().addAppender(appender)
    }

    @AfterEach
    fun detachAppender() {
        rootLogger().detachAppender(appender)
        appender.stop()
        appender.list.clear()
    }

    @Test
    fun wireLoggerNameStaysUnderTheInfoLeveledNamespace() {
        assertTrue(
            WIRE_LOGGER_NAME.startsWith("com.fuelingchat."),
            "the wire logger must stay under com.fuelingchat or logback.xml silences it: $WIRE_LOGGER_NAME",
        )
    }

    @Test
    fun ollamaTurnEmitsRequestAndResponseLinesWithTheFullWireJson() =
        runBlocking {
            val client = ollamaClient(listOf(assistantText(FINAL_TEXT)))

            client.run(SYSTEM_PROMPT, emptyList(), USER_MESSAGE, emptyList())

            val requestBody = bodyOf(requestLines().single())
            assertEquals(OLLAMA_MODEL, requestBody.string("model"), "the configured OLLAMA_MODEL must be sent")
            assertTrue(requestBody["messages"]!!.jsonArray.isNotEmpty(), "the messages array must be logged in full")
            assertFalse(requestBody["stream"]!!.jsonPrimitive.boolean, "D-7: no streaming request may be sent")

            val responseBody = bodyOf(responseLines().single())
            assertEquals(OLLAMA_MODEL, responseBody.string("model"))
            assertEquals(FINAL_TEXT, responseBody["message"]!!.jsonObject.string("content"))
            assertTrue(responseBody["done"]!!.jsonPrimitive.boolean, "the wire response must be logged in full")
        }

    @Test
    fun deepSeekTurnEmitsRequestAndResponseLinesWithTheFullWireJson() =
        runBlocking {
            val client = deepSeekClient(listOf(textCompletion()))

            client.run(SYSTEM_PROMPT, emptyList(), USER_MESSAGE, emptyList())

            val requestBody = bodyOf(requestLines().single())
            assertEquals(DEEPSEEK_MODEL, requestBody.string("model"), "the configured DEEPSEEK_MODEL must be sent")
            assertTrue(requestBody["messages"]!!.jsonArray.isNotEmpty(), "the messages array must be logged in full")

            val responseBody = bodyOf(responseLines().single())
            val choice = responseBody["choices"]!!.jsonArray.single().jsonObject
            assertEquals(FINAL_TEXT, choice["message"]!!.jsonObject.string("content"))
            assertEquals("stop", choice.string("finish_reason"))
            assertTrue(responseLines().single().formattedMessage.contains("elapsed_ms="), "the turn must be timed")
        }

    @Test
    fun bothWireLinesCarryTheCorrelationIdOfTheChatRequest() =
        testApplication {
            application {
                installRequestLogging()
                routing {
                    post("/chat") {
                        val client = ollamaClient(listOf(assistantText(FINAL_TEXT)))
                        call.respondText(client.run(SYSTEM_PROMPT, emptyList(), USER_MESSAGE, emptyList()))
                    }
                }
            }

            client.post("/chat") { header(HttpHeaders.XRequestId, "req-wire-1") }

            val requestEvent = awaitWireLines("llm request: ").single()
            val responseEvent = awaitWireLines("llm response: ").single()
            assertEquals("req-wire-1", requestEvent.mdcPropertyMap["correlation_id"])
            assertEquals("req-wire-1", responseEvent.mdcPropertyMap["correlation_id"])
        }

    @Test
    fun secretShapedFieldsInTheLoggedJsonAreMaskedBeforeWrite() =
        runBlocking {
            val engine =
                MockEngine {
                    respond(
                        content = """{"message":{"role":"assistant","content":"ok","token":"tok-wire-999"}}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val client =
                HttpClient(engine) {
                    install(LlmWireLogging) {
                        provider = LOCAL_PROVIDER
                        model = "test-model"
                        logger = this@LlmWireLoggingTest.logger
                    }
                }

            client.post("http://127.0.0.1:9/v1/chat") {
                contentType(ContentType.Application.Json)
                setBody("""{"model":"test-model","api_key":"sk-raw-wire-123"}""")
            }

            val messages = appender.list.joinToString("\n") { it.formattedMessage }
            assertTrue(messages.contains(""""api_key":"***""""), "the request JSON secret must be masked: $messages")
            assertTrue(messages.contains(""""token":"***""""), "the response JSON secret must be masked: $messages")
            assertFalse(messages.contains("sk-raw-wire-123"), "the raw request secret leaked: $messages")
            assertFalse(messages.contains("tok-wire-999"), "the raw response secret leaked: $messages")
        }

    private fun ollamaClient(responses: List<String>): LlmClient {
        val engine =
            MockEngine {
                respond(
                    content = responses.first(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        val factory =
            llmWireLoggingFactory(
                provider = LOCAL_PROVIDER,
                model = OLLAMA_MODEL,
                logger = logger,
                baseClient = HttpClient(engine),
            )
        return OllamaLlmClient(config(), factory)
    }

    private fun deepSeekClient(responses: List<String>): LlmClient {
        val engine =
            MockEngine {
                respond(
                    content = responses.first(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        val factory =
            llmWireLoggingFactory(
                provider = REMOTE_PROVIDER,
                model = DEEPSEEK_MODEL,
                logger = logger,
                baseClient = HttpClient(engine),
            )
        return DeepSeekLlmClient(config(), factory)
    }

    private fun config(): AppConfig {
        val file = tempDir.resolve("wire-logging-test.env")
        Files.writeString(
            file,
            """
            DB_PASSWORD=test-db
            DEEPSEEK_API_KEY=$DEEPSEEK_API_KEY
            DEEPSEEK_MODEL=$DEEPSEEK_MODEL
            OLLAMA_MODEL=$OLLAMA_MODEL
            OLLAMA_BASE_URL=$OLLAMA_BASE_URL
            """.trimIndent(),
        )
        return AppConfig(file)
    }

    private fun requestLines(): List<ILoggingEvent> = awaitWireLines("llm request: ")

    private fun responseLines(): List<ILoggingEvent> = awaitWireLines("llm response: ")

    private fun bodyOf(event: ILoggingEvent): JsonObject =
        Json.parseToJsonElement(event.formattedMessage.substringAfter("body=")).jsonObject

    private fun awaitWireLines(prefix: String): List<ILoggingEvent> {
        val deadline = System.nanoTime() + 2_000_000_000L
        while (System.nanoTime() < deadline) {
            val events = appender.list.filter { it.formattedMessage.startsWith(prefix) }
            if (events.isNotEmpty()) return events
            Thread.sleep(10)
        }
        return appender.list.filter { it.formattedMessage.startsWith(prefix) }
    }

    private fun rootLogger(): Logger =
        (LoggerFactory.getILoggerFactory() as LoggerContext).getLogger(Logger.ROOT_LOGGER_NAME)

    private fun assistantText(text: String): String =
        """{"model":"$OLLAMA_MODEL","message":{"role":"assistant","content":"$text"},"done":true}"""

    private fun textCompletion(): String =
        """
        {
          "id": "chatcmpl-fake-text",
          "object": "chat.completion",
          "created": 1700000000,
          "model": "$DEEPSEEK_MODEL",
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
}

private fun JsonObject.string(key: String): String = this[key]!!.jsonPrimitive.content
