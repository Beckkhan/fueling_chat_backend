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
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Route tests for `POST /chat` (CONTRACT-API-001 / CONTRACT-API-002) against a private fake [ChatService].
 *
 * Each test builds its own application module — ContentNegotiation json plus the route with the fake — so
 * nothing here touches AppModule or the production DI graph: no network, no database, no Koog.
 */
class ChatRoutesTest {
    @Test
    fun successReturnsExactlyTheThreeContractKeys() =
        testApplication {
            chatApp(FakeChatService { prompt, _, _ -> ChatTurn("summary for: $prompt", "local") })

            val response =
                client.postChat("""{"prompt": "What did fueling F-10001 cost?", "fueling_id": "F-10001"}""")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = parseJson(response.bodyAsText())
            assertEquals(setOf("summary", "provider", "elapsed_ms"), body.keys)
            assertEquals("summary for: What did fueling F-10001 cost?", body.string("summary"))
            assertEquals("local", body.string("provider"))
            val elapsedMs = body["elapsed_ms"]!!.jsonPrimitive
            assertFalse(elapsedMs.isString, "elapsed_ms must be a JSON integer, got: $elapsedMs")
            val elapsed = elapsedMs.intOrNull
            assertTrue(elapsed != null && elapsed >= 0, "elapsed_ms must be an integer >= 0, got: $elapsedMs")
        }

    @Test
    fun blankPromptIsRejectedWithMasked400() =
        testApplication {
            val fake = FakeChatService { prompt, _, _ -> ChatTurn("summary for: $prompt", "local") }
            chatApp(fake)

            val empty = client.postChat("""{"prompt": ""}""")
            // The JSON escapes below decode to a tab and a newline: whitespace-only, still a blank prompt.
            val whitespaceOnly = client.postChat("""{"prompt": " \t\n "}""")

            assertEquals(HttpStatusCode.BadRequest, empty.status)
            assertEquals(HttpStatusCode.BadRequest, whitespaceOnly.status)
            assertMaskedError(empty.bodyAsText())
            assertMaskedError(whitespaceOnly.bodyAsText())
            assertTrue(fake.calls.isEmpty(), "a blank prompt must be rejected before the service seam")
        }

    @Test
    fun invalidProviderIsRejectedWithMasked400() =
        testApplication {
            val fake = FakeChatService { prompt, _, _ -> ChatTurn("summary for: $prompt", "local") }
            chatApp(fake)

            val response = client.postChat("""{"prompt": "what did I pay?", "provider": "cloud"}""")

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertMaskedError(response.bodyAsText())
            assertTrue(fake.calls.isEmpty(), "an invalid provider must be rejected before the service seam")
        }

    @Test
    fun malformedJsonIsRejectedWithMasked400() =
        testApplication {
            chatApp(FakeChatService { prompt, _, _ -> ChatTurn("summary for: $prompt", "local") })

            val truncated = client.postChat("""{"prompt": "leak-me-please" """)
            val wrongContentType = client.postChat("prompt=what+did+I+pay", ContentType.Text.Plain)

            assertEquals(HttpStatusCode.BadRequest, truncated.status)
            assertEquals(HttpStatusCode.BadRequest, wrongContentType.status)
            val truncatedBody = truncated.bodyAsText()
            val wrongContentTypeBody = wrongContentType.bodyAsText()
            assertMaskedError(truncatedBody)
            assertMaskedError(wrongContentTypeBody)
            assertFalse(truncatedBody.contains("leak-me-please"), "the raw request body leaked: $truncatedBody")
        }

    @Test
    fun chatFailure500MapsToHttp500WithItsMaskedMessage() =
        testApplication {
            chatApp(FakeChatService { _, _, _ -> throw ChatFailure(500, "fueling database unavailable") })

            val response = client.postChat("""{"prompt": "what did I pay?"}""")

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertEquals("fueling database unavailable", assertMaskedError(response.bodyAsText()))
        }

    @Test
    fun chatFailure502MapsToHttp502WithItsMaskedMessage() =
        testApplication {
            chatApp(FakeChatService { _, _, _ -> throw ChatFailure(502, "provider unreachable") })

            val response = client.postChat("""{"prompt": "what did I pay?"}""")

            assertEquals(HttpStatusCode.BadGateway, response.status)
            assertEquals("provider unreachable", assertMaskedError(response.bodyAsText()))
        }

    @Test
    fun unmappedExceptionIsMaskedAsGeneric500() =
        testApplication {
            val sentinel = "raw-internal-detail-must-not-leak"
            chatApp(FakeChatService { _, _, _ -> throw IllegalStateException("$sentinel SELECT * FROM fuelings") })

            val response = client.postChat("""{"prompt": "what did I pay?"}""")

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            val text = response.bodyAsText()
            assertMaskedError(text)
            assertFalse(text.contains(sentinel), "the raw exception message leaked into the body: $text")
            assertFalse(text.contains("SELECT"), "raw SQL leaked into the body: $text")
        }

    @Test
    fun fuelingIdAndProviderReachTheServiceUnchanged() =
        testApplication {
            val fake = FakeChatService { _, _, provider -> ChatTurn("served by $provider", "remote") }
            chatApp(fake)

            val response =
                client.postChat("""{"prompt": "what did I pay?", "fueling_id": "F-10001", "provider": "remote"}""")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("served by remote", parseJson(response.bodyAsText()).string("summary"))
            val call = fake.calls.single()
            assertEquals("what did I pay?", call.prompt)
            assertEquals("F-10001", call.fuelingId)
            assertEquals("remote", call.provider)
        }

    @Test
    fun absentProviderReachesTheServiceAsNull() =
        testApplication {
            val fake = FakeChatService { _, _, _ -> ChatTurn("summary", "local") }
            chatApp(fake)

            val response = client.postChat("""{"prompt": "what did I pay?", "fueling_id": "F-10001"}""")

            assertEquals(HttpStatusCode.OK, response.status)
            val call = fake.calls.single()
            assertEquals("F-10001", call.fuelingId)
            assertNull(call.provider)
        }

    private fun ApplicationTestBuilder.chatApp(service: ChatService) {
        application {
            install(ContentNegotiation) {
                json()
            }
            routing {
                chatRoutes { service }
            }
        }
    }
}

private suspend fun HttpClient.postChat(
    body: String,
    type: ContentType = ContentType.Application.Json,
): HttpResponse =
    post("/chat") {
        contentType(type)
        setBody(body)
    }

/** Records the seam arguments before delegating to [behavior], so tests can assert what the route passed. */
private class FakeChatService(
    private val behavior: (String, String?, String?) -> ChatTurn,
) : ChatService {
    val calls = mutableListOf<RecordedCall>()

    override suspend fun runTurn(
        prompt: String,
        fuelingId: String?,
        provider: String?,
    ): ChatTurn {
        calls += RecordedCall(prompt, fuelingId, provider)
        return behavior(prompt, fuelingId, provider)
    }
}

private data class RecordedCall(
    val prompt: String,
    val fuelingId: String?,
    val provider: String?,
)

private fun parseJson(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

private fun JsonObject.string(key: String): String = this[key]!!.jsonPrimitive.content

/**
 * Asserts the body is exactly `{"error": "<masked message>"}` and that the message leaks no internals
 * (CONTRACT-API-001), then returns the message.
 */
private fun assertMaskedError(text: String): String {
    val body = parseJson(text)
    assertEquals(setOf("error"), body.keys, "an error body must carry exactly the masked message: $text")
    val message = body.string("error")
    assertTrue(message.isNotBlank(), "the masked message must not be blank: $text")
    assertFalse(message.contains("Exception"), "the masked body leaked an exception type: $text")
    assertFalse(message.contains("kotlin."), "the masked body leaked Kotlin internals: $text")
    assertFalse(message.contains("io.ktor."), "the masked body leaked Ktor internals: $text")
    assertFalse(message.contains("java.lang."), "the masked body leaked JVM internals: $text")
    return message
}
