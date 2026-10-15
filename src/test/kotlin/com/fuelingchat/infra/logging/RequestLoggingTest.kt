package com.fuelingchat.infra.logging

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

/**
 * Pins the production [installRequestLogging] seam: one request line per call in the turbo shape,
 * with the correlation id (X-Request-Id or a generated UUID) propagated through MDC to every log
 * line emitted during the call. Health/probe paths stay silent.
 */
class RequestLoggingTest {
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
    }

    @Test
    fun requestLineCarriesTheProvidedCorrelationIdInTurboShape() =
        testApplication {
            application {
                installRequestLogging()
                routing {
                    get("/ping") { call.respondText("pong") }
                }
            }

            client.get("/ping") { header(HttpHeaders.XRequestId, "req-123") }

            val event = awaitRequestEvents().single()
            assertEquals("req-123", event.mdcPropertyMap["correlation_id"])
            assertEquals("GET", event.mdcPropertyMap["http_method"])
            assertEquals("/ping", event.mdcPropertyMap["http_path"])
            assertEquals("HTTP/1.1", event.mdcPropertyMap["http_version"])
            assertTrue(
                event.formattedMessage.matches(Regex("""GET /ping HTTP/1\.1 200 \d+ .*""")),
                "unexpected request line: ${event.formattedMessage}",
            )
        }

    @Test
    fun missingXRequestIdFallsBackToAGeneratedUuid() =
        testApplication {
            application {
                installRequestLogging()
                routing {
                    get("/ping") { call.respondText("pong") }
                }
            }

            client.get("/ping")

            val correlationId = awaitRequestEvents().single().mdcPropertyMap["correlation_id"]
            assertTrue(
                correlationId != null && correlationId.matches(UUID_REGEX),
                "expected a generated UUID, got: $correlationId",
            )
        }

    @Test
    fun serviceLayerLogsEmittedDuringTheCallCarryTheCorrelationId() =
        testApplication {
            application {
                installRequestLogging()
                routing {
                    get("/ping") {
                        LoggerFactory.getLogger(HANDLER_LOGGER_NAME).info("handling ping")
                        call.respondText("pong")
                    }
                }
            }

            client.get("/ping") { header(HttpHeaders.XRequestId, "req-456") }

            val handlerEvents = awaitEvents { it.loggerName == HANDLER_LOGGER_NAME }
            assertEquals(1, handlerEvents.size)
            assertEquals("req-456", handlerEvents.single().mdcPropertyMap["correlation_id"])
        }

    @Test
    fun ignoredPathsProduceNoRequestLines() =
        testApplication {
            application {
                installRequestLogging()
                routing {
                    IGNORE_PATHS.forEach { path -> get(path) { call.respondText("ok") } }
                    get("/ping") { call.respondText("pong") }
                }
            }

            IGNORE_PATHS.forEach { client.get(it) }
            client.get("/ping")

            val events = awaitRequestEvents()
            assertEquals(1, events.size, "only /ping may be logged, got: ${events.map { it.formattedMessage }}")
            assertEquals("/ping", events.single().mdcPropertyMap["http_path"])
        }

    /** [CallLogging] emits on ResponseSent, which may race the test client's return: poll briefly. */
    private fun awaitRequestEvents(): List<ILoggingEvent> = awaitEvents { it.loggerName == REQUEST_LOGGER_NAME }

    private fun awaitEvents(predicate: (ILoggingEvent) -> Boolean): List<ILoggingEvent> {
        val deadline = System.nanoTime() + 2_000_000_000L
        while (System.nanoTime() < deadline) {
            val events = appender.list.filter(predicate)
            if (events.isNotEmpty()) return events
            Thread.sleep(10)
        }
        return appender.list.filter(predicate)
    }

    private fun rootLogger(): Logger =
        (LoggerFactory.getILoggerFactory() as LoggerContext).getLogger(Logger.ROOT_LOGGER_NAME)

    private companion object {
        const val REQUEST_LOGGER_NAME = "com.fuelingchat.request"
        const val HANDLER_LOGGER_NAME = "com.fuelingchat.infra.logging.RequestLoggingTest"
        val UUID_REGEX = Regex("""^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$""")
        val IGNORE_PATHS = setOf("/metrics", "/readiness", "/liveness", "/health-check", "/info", "/time")
    }
}
