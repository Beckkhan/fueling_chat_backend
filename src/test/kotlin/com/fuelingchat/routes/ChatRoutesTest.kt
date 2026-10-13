package com.fuelingchat.routes

import com.fuelingchat.service.ChatFailure
import com.fuelingchat.service.ChatService
import com.fuelingchat.service.ChatTurn
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatRoutesTest {
    @Test
    fun returns200WithExactContractShape() =
        testApplication {
            val fake = FakeChatService()
            application { configureChat(fake) }

            val response = client.postChat("""{"prompt":"hi","fueling_id":"F-10001","provider":"local"}""")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals(setOf("summary", "provider", "elapsed_ms"), body.keys)
            assertEquals("answer", body.getValue("summary").jsonPrimitive.content)
            assertEquals("local", body.getValue("provider").jsonPrimitive.content)
            assertTrue(
                body
                    .getValue("elapsed_ms")
                    .jsonPrimitive
                    .content
                    .toLong() >= 0,
            )
            assertEquals("hi", fake.lastPrompt)
            assertEquals("F-10001", fake.lastFuelingId)
            assertEquals("local", fake.lastProvider)
        }

    @Test
    fun defaultsProviderToLocalWhenAbsent() =
        testApplication {
            val fake = FakeChatService()
            application { configureChat(fake) }

            val response = client.postChat("""{"prompt":"hi"}""")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("local", body.getValue("provider").jsonPrimitive.content)
            assertEquals("local", fake.lastProvider)
            assertNull(fake.lastFuelingId)
        }

    @Test
    fun blankPromptReturns400() =
        testApplication {
            val fake = FakeChatService()
            application { configureChat(fake) }

            val response = client.postChat("""{"prompt":"   "}""")

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("prompt must not be blank", errorMessage(response))
        }

    @Test
    fun invalidProviderReturns400() =
        testApplication {
            val fake = FakeChatService()
            application { configureChat(fake) }

            val response = client.postChat("""{"prompt":"hi","provider":"cloud"}""")

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("provider must be \"local\" or \"remote\"", errorMessage(response))
        }

    @Test
    fun malformedJsonReturns400Masked() =
        testApplication {
            val fake = FakeChatService()
            application { configureChat(fake) }

            val response = client.postChat("{oops")

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("invalid request body", errorMessage(response))
        }

    @Test
    fun missingPromptFieldReturns400() =
        testApplication {
            val fake = FakeChatService()
            application { configureChat(fake) }

            val response = client.postChat("""{"provider":"remote"}""")

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("invalid request body", errorMessage(response))
        }

    @Test
    fun dbFailureMapsTo500WithMaskedMessage() =
        testApplication {
            val fake =
                FakeChatService().apply {
                    failure = ChatFailure(ChatFailure.DB_FAILURE, "fueling database unavailable")
                }
            application { configureChat(fake) }

            val response = client.postChat("""{"prompt":"hi"}""")

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertEquals("fueling database unavailable", errorMessage(response))
        }

    @Test
    fun providerFailureMapsTo502WithMaskedMessage() =
        testApplication {
            val fake =
                FakeChatService().apply {
                    failure = ChatFailure(ChatFailure.PROVIDER_FAILURE, "provider unreachable")
                }
            application { configureChat(fake) }

            val response = client.postChat("""{"prompt":"hi"}""")

            assertEquals(HttpStatusCode.BadGateway, response.status)
            assertEquals("provider unreachable", errorMessage(response))
        }

    @Test
    fun unexpectedServiceExceptionReturnsGenericMasked500() =
        testApplication {
            val fake =
                FakeChatService().apply {
                    exception = IllegalStateException("jdbc:postgresql://db secret hunter2")
                }
            application { configureChat(fake) }

            val response = client.postChat("""{"prompt":"hi"}""")

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertEquals("internal server error", errorMessage(response))
            assertFalse(response.bodyAsText().contains("hunter2"))
            assertFalse(response.bodyAsText().contains("jdbc"))
        }

    @Test
    fun elapsedMsIsNonNegative() =
        testApplication {
            val fake = FakeChatService().apply { delayMs = 50 }
            application { configureChat(fake) }

            val response = client.postChat("""{"prompt":"hi"}""")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertTrue(
                body
                    .getValue("elapsed_ms")
                    .jsonPrimitive
                    .content
                    .toLong() >= 0,
            )
        }

    private class FakeChatService : ChatService {
        var turn: ChatTurn = ChatTurn("answer", "local")
        var failure: ChatFailure? = null
        var exception: Exception? = null
        var delayMs: Long = 0L
        var lastPrompt: String? = null
        var lastFuelingId: String? = null
        var lastProvider: String? = null

        override suspend fun chat(
            prompt: String,
            fuelingId: String?,
            provider: String,
        ): ChatTurn {
            lastPrompt = prompt
            lastFuelingId = fuelingId
            lastProvider = provider
            delay(delayMs)
            failure?.let { throw it }
            exception?.let { throw it }
            return turn
        }
    }

    private suspend fun HttpClient.postChat(body: String): HttpResponse =
        post("/chat") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun errorMessage(response: HttpResponse): String =
        Json
            .parseToJsonElement(response.bodyAsText())
            .jsonObject
            .getValue("error")
            .jsonPrimitive
            .content
}
