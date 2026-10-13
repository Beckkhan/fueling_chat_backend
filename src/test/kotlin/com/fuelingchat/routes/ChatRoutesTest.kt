package com.fuelingchat.routes

import com.fuelingchat.service.ChatFailure
import com.fuelingchat.service.ChatFailureStatus
import com.fuelingchat.service.ChatService
import com.fuelingchat.service.ChatServiceRequest
import com.fuelingchat.service.ChatTurn
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Fake seam for route tests: returns [turn], or throws [failure]/[unexpected] when set.
 */
private class FakeChatService(
    private val turn: ChatTurn,
    private val failure: ChatFailure? = null,
    private val unexpected: RuntimeException? = null,
) : ChatService {
    override suspend fun run(request: ChatServiceRequest): ChatTurn {
        unexpected?.let { throw it }
        failure?.let { throw it }
        return turn
    }
}

class ChatRoutesTest {
    @Test
    fun ok200ReturnsExactlyThreeFieldsAndNonNegativeElapsed() {
        testApplication {
            val fake =
                FakeChatService(
                    turn = ChatTurn(summary = "F-10001 completed at Tverskaya 7.", provider = "local"),
                )
            application { chatRoutes { fake } }

            val requestBody =
                """{"prompt":"What do we know about F-10001?","fueling_id":"F-10001","provider":"local"}"""
            val response =
                client.post("/chat") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseContentType = response.contentType()
            assertTrue(
                responseContentType != null && responseContentType.match(ContentType.Application.Json),
                "200 response must be application/json",
            )
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertFrozenSuccessShape(body, "F-10001 completed at Tverskaya 7.", "local")
        }
    }

    @Test
    fun blankPromptReturns400MaskedError() {
        testApplication {
            val fake = FakeChatService(turn = ChatTurn(summary = "unused", provider = "local"))
            application { chatRoutes { fake } }

            val response =
                client.post("/chat") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"prompt":""}""")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals(setOf("error"), body.keys)
            assertTrue(
                body
                    .getValue("error")
                    .jsonPrimitive.content
                    .isNotBlank(),
                "masked error message must be non-blank",
            )
        }
    }

    @Test
    fun whitespaceOnlyPromptReturns400MaskedError() {
        testApplication {
            val fake = FakeChatService(turn = ChatTurn(summary = "unused", provider = "local"))
            application { chatRoutes { fake } }

            val response =
                client.post("/chat") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"prompt":"   "}""")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals(setOf("error"), body.keys)
            val error = body.getValue("error").jsonPrimitive.content
            assertTrue(error.isNotBlank(), "masked error message must be non-blank")
            assertFalse(error.contains("   "), "raw prompt must not be echoed")
        }
    }

    @Test
    fun invalidProviderReturns400MaskedError() {
        testApplication {
            val fake = FakeChatService(turn = ChatTurn(summary = "answer", provider = "local"))
            application { chatRoutes { fake } }

            val invalid =
                client.post("/chat") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"prompt":"Hello","provider":"other"}""")
                }
            assertEquals(HttpStatusCode.BadRequest, invalid.status)
            val body = Json.parseToJsonElement(invalid.bodyAsText()).jsonObject
            assertEquals(setOf("error"), body.keys)
            assertTrue(
                body
                    .getValue("error")
                    .jsonPrimitive.content
                    .isNotBlank(),
                "masked error message must be non-blank",
            )

            // D-10: an absent provider is not an error — it defaults to "local".
            val defaulted =
                client.post("/chat") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"prompt":"Hello"}""")
                }
            assertEquals(HttpStatusCode.OK, defaulted.status)
        }
    }

    @Test
    fun malformedJsonReturns400MaskedError() {
        testApplication {
            val fake = FakeChatService(turn = ChatTurn(summary = "unused", provider = "local"))
            application { chatRoutes { fake } }

            val malformedBody = """{"prompt": }"""
            val response =
                client.post("/chat") {
                    contentType(ContentType.Application.Json)
                    setBody(malformedBody)
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals(setOf("error"), body.keys)
            val error = body.getValue("error").jsonPrimitive.content
            assertTrue(error.isNotBlank(), "masked error message must be non-blank")
            assertFalse(error.contains(malformedBody), "raw request body must not be echoed")
        }
    }

    @Test
    fun databaseFailureClassReturnsMasked500() {
        testApplication {
            val maskedMessage = "Fueling data is temporarily unavailable"
            val fake =
                FakeChatService(
                    turn = ChatTurn(summary = "unused", provider = "local"),
                    failure = ChatFailure(ChatFailureStatus.DATABASE, maskedMessage),
                )
            application { chatRoutes { fake } }

            val response =
                client.post("/chat") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"prompt":"What do we know about F-10001?"}""")
                }

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            val bodyText = response.bodyAsText()
            val body = Json.parseToJsonElement(bodyText).jsonObject
            assertEquals(setOf("error"), body.keys)
            assertEquals(maskedMessage, body.getValue("error").jsonPrimitive.content)
            assertFalse(bodyText.contains("ChatFailure"), "failure class name must not leak")
            assertFalse(bodyText.contains("Exception"), "exception type must not leak")
            assertFalse(bodyText.contains("com.fuelingchat"), "stack frame must not leak")
        }
    }

    @Test
    fun providerFailureClassReturnsMasked502() {
        testApplication {
            val maskedMessage = "The model provider is temporarily unreachable"
            val fake =
                FakeChatService(
                    turn = ChatTurn(summary = "unused", provider = "local"),
                    failure = ChatFailure(ChatFailureStatus.PROVIDER, maskedMessage),
                )
            application { chatRoutes { fake } }

            val response =
                client.post("/chat") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"prompt":"What do we know about F-10001?"}""")
                }

            assertEquals(HttpStatusCode.BadGateway, response.status)
            val bodyText = response.bodyAsText()
            val body = Json.parseToJsonElement(bodyText).jsonObject
            assertEquals(setOf("error"), body.keys)
            assertEquals(maskedMessage, body.getValue("error").jsonPrimitive.content)
            assertFalse(bodyText.contains("ChatFailure"), "failure class name must not leak")
            assertFalse(bodyText.contains("Exception"), "exception type must not leak")
            assertFalse(bodyText.contains("com.fuelingchat"), "stack frame must not leak")
        }
    }

    @Test
    fun unmappedExceptionReturnsGenericMasked500() {
        testApplication {
            val rawDetail = "SQLSTATE 08001 connection refused; password=hunter2"
            val fake =
                FakeChatService(
                    turn = ChatTurn(summary = "unused", provider = "local"),
                    unexpected = RuntimeException(rawDetail),
                )
            application { chatRoutes { fake } }

            val response =
                client.post("/chat") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"prompt":"What do we know about F-10001?"}""")
                }

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            val bodyText = response.bodyAsText()
            val body = Json.parseToJsonElement(bodyText).jsonObject
            assertEquals(setOf("error"), body.keys)
            val error = body.getValue("error").jsonPrimitive.content
            assertTrue(error.isNotBlank(), "masked error message must be non-blank")
            assertNoInternalDetailsLeak(bodyText, rawDetail)
        }
    }

    /** Asserts the frozen CONTRACT-API-002 200 shape with elapsed_ms as an integer >= 0. */
    private fun assertFrozenSuccessShape(
        body: JsonObject,
        expectedSummary: String,
        expectedProvider: String,
    ) {
        assertEquals(setOf("summary", "provider", "elapsed_ms"), body.keys)
        assertEquals(expectedSummary, body.getValue("summary").jsonPrimitive.content)
        assertEquals(expectedProvider, body.getValue("provider").jsonPrimitive.content)
        val elapsedMs = body.getValue("elapsed_ms").jsonPrimitive
        assertFalse(elapsedMs.isString, "elapsed_ms must be a JSON number")
        assertTrue(elapsedMs.long >= 0L, "elapsed_ms must be >= 0 but was ${elapsedMs.long}")
    }

    /** Asserts the masked body leaks no raw exception detail, credentials or stack frames. */
    private fun assertNoInternalDetailsLeak(
        bodyText: String,
        rawDetail: String,
    ) {
        assertFalse(bodyText.contains(rawDetail), "raw exception message must not leak")
        assertFalse(bodyText.contains("hunter2"), "credentials must not leak")
        assertFalse(bodyText.contains("RuntimeException"), "exception type must not leak")
        assertFalse(bodyText.contains("com.fuelingchat"), "stack frame must not leak")
        assertFalse(bodyText.contains("\tat "), "stack trace must not leak")
    }
}
