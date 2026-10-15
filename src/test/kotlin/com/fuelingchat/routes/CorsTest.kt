package com.fuelingchat.routes

import com.fuelingchat.service.ChatService
import com.fuelingchat.service.ChatTurn
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * CORS tests for the browser contract of `POST /chat` (D-13): the production [installCors] seam is installed
 * exactly as AppModule does, so these tests pin the headers a browser origin actually receives.
 */
class CorsTest {
    @Test
    fun preflightAllowsTheFrontendOriginAndContentType() =
        testApplication {
            chatAppWithCors()

            val response =
                client.options("/chat") {
                    header(HttpHeaders.Origin, FRONTEND_ORIGIN)
                    header(HttpHeaders.AccessControlRequestMethod, HttpMethod.Post.value)
                    header(HttpHeaders.AccessControlRequestHeaders, HttpHeaders.ContentType)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])
            // Ktor omits Access-Control-Allow-Methods when only its default methods (GET/POST/HEAD) are
            // allowed. Browsers accept the absence for POST: it is a CORS-safelisted method and the
            // frontend's fetch uses the default (non-"include") credentials mode (Fetch spec, preflight steps).
            val methods = response.headers[HttpHeaders.AccessControlAllowMethods]
            assertTrue(
                methods == null ||
                    methods.split(",").map(String::trim).any { it.equals(HttpMethod.Post.value, ignoreCase = true) },
                "if Allow-Methods is present it must include POST, got: $methods",
            )
            val headers = response.headers[HttpHeaders.AccessControlAllowHeaders].orEmpty()
            assertTrue(
                headers.split(",").map(String::trim).any { it.equals(HttpHeaders.ContentType, ignoreCase = true) },
                "Content-Type must be an allowed header, got: $headers",
            )
        }

    @Test
    fun actualPostCarriesTheAllowOriginHeader() =
        testApplication {
            chatAppWithCors()

            val response =
                client.postChatWithOrigin("""{"prompt": "what did I pay?", "provider": "local"}""")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])
        }

    @Test
    fun foreignOriginIsStillAllowedButRequestsWithoutOriginCarryNoCorsHeaders() =
        testApplication {
            chatAppWithCors()

            val withOtherOrigin =
                client.postChatWithOrigin(
                    """{"prompt": "what did I pay?"}""",
                    origin = "https://fueling.example.com",
                )
            val withoutOrigin = client.postChatWithOrigin("""{"prompt": "what did I pay?"}""", origin = null)

            assertEquals(HttpStatusCode.OK, withOtherOrigin.status)
            assertEquals("*", withOtherOrigin.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals(HttpStatusCode.OK, withoutOrigin.status)
            assertNull(withoutOrigin.headers[HttpHeaders.AccessControlAllowOrigin])
        }

    private fun ApplicationTestBuilder.chatAppWithCors() {
        application {
            install(ContentNegotiation) {
                json()
            }
            installCors()
            routing {
                chatRoutes { EchoService() }
            }
        }
    }
}

private const val FRONTEND_ORIGIN = "http://localhost:5173"

private class EchoService : ChatService {
    override suspend fun runTurn(
        prompt: String,
        fuelingId: String?,
        provider: String?,
    ): ChatTurn = ChatTurn("summary for: $prompt", provider ?: "local")
}

private suspend fun HttpClient.postChatWithOrigin(
    body: String,
    origin: String? = FRONTEND_ORIGIN,
): HttpResponse =
    post("/chat") {
        contentType(ContentType.Application.Json)
        setBody(body)
        if (origin != null) header(HttpHeaders.Origin, origin)
    }
