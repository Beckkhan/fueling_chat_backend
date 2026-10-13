package com.fuelingchat.routes

import com.fuelingchat.service.ChatFailure
import com.fuelingchat.service.ChatService
import com.fuelingchat.service.ChatTurn
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Route tests per T11 acceptance: testApplication with a fake ChatService seam.
 * No network, no DB, no LLM — each test wires a canned ChatService outcome.
 */
class ChatRoutesTest {
    @Test
    fun returns200WithExactContractShape() =
        testApplication {
            chatApp(FakeChatService(turn = ChatTurn("summary", "local")))

            val response = postChat("""{"prompt":"hello","provider":"remote"}""")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals(setOf("summary", "provider", "elapsed_ms"), body.keys)
            assertEquals("summary", body.getValue("summary").jsonPrimitive.content)
            assertEquals("local", body.getValue("provider").jsonPrimitive.content)
        }

    @Test
    fun returns400ForBlankPrompt() =
        testApplication {
            chatApp(FakeChatService())

            val response = postChat("""{"prompt":"   "}""")

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("Prompt must not be blank", maskedErrorOf(response.bodyAsText()))
        }

    @Test
    fun returns400ForInvalidProvider() =
        testApplication {
            chatApp(FakeChatService())

            val response = postChat("""{"prompt":"hello","provider":"banana"}""")

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("Invalid provider", maskedErrorOf(response.bodyAsText()))
        }

    @Test
    fun returns400ForMalformedJson() =
        testApplication {
            chatApp(FakeChatService())

            val response = postChat("not json at all")

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("Invalid request body", maskedErrorOf(response.bodyAsText()))
        }

    @Test
    fun returnsMasked500ForDbFailureClass() =
        testApplication {
            chatApp(FakeChatService(failure = ChatFailure.databaseFailure()))

            val response = postChat("""{"prompt":"hello"}""")

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertEquals("Databases unavailable", maskedErrorOf(response.bodyAsText()))
        }

    @Test
    fun returnsMasked502ForProviderFailureClass() =
        testApplication {
            chatApp(FakeChatService(failure = ChatFailure.providerFailure()))

            val response = postChat("""{"prompt":"hello"}""")

            assertEquals(HttpStatusCode.BadGateway, response.status)
            assertEquals("Provider unreachable", maskedErrorOf(response.bodyAsText()))
        }

    @Test
    fun unmappedExceptionReturnsGenericMasked500() =
        testApplication {
            val leaky = RuntimeException("SQL state 28P01: password=supersecret for user fuelings")
            chatApp(FakeChatService(failure = leaky))

            val response = postChat("""{"prompt":"hello"}""")

            val bodyText = response.bodyAsText()
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertEquals("Internal server error", maskedErrorOf(bodyText))
            assertFalse(bodyText.contains("supersecret"), "credentials leaked: $bodyText")
            assertFalse(bodyText.contains("SQL"), "internals leaked: $bodyText")
        }

    @Test
    fun elapsedMsIsNonNegative() =
        testApplication {
            chatApp(FakeChatService(turn = ChatTurn("summary", "local")))

            val response = postChat("""{"prompt":"hello"}""")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val elapsed = body.getValue("elapsed_ms").jsonPrimitive
            assertFalse(elapsed.isString, "elapsed_ms must be a JSON integer, was: $elapsed")
            assertTrue(elapsed.long >= 0, "elapsed_ms must be ≥ 0, was: ${elapsed.long}")
        }

    private fun ApplicationTestBuilder.chatApp(chatService: ChatService) {
        application {
            configureChatStatusPages()
            routing { chatRoutes { chatService } }
        }
    }

    private suspend fun ApplicationTestBuilder.postChat(body: String): HttpResponse =
        client.post("/chat") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private fun maskedErrorOf(bodyText: String): String {
        val body = Json.parseToJsonElement(bodyText).jsonObject
        assertEquals(setOf("error"), body.keys)
        return body.getValue("error").jsonPrimitive.content
    }

    private class FakeChatService(
        private val turn: ChatTurn = ChatTurn("summary", "local"),
        private val failure: Throwable? = null,
    ) : ChatService {
        override suspend fun chat(
            prompt: String,
            fuelingId: String?,
            provider: String?,
        ): ChatTurn {
            failure?.let { throw it }
            return turn
        }
    }
}
