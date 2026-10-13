package com.fuelingchat.routes

import com.fuelingchat.service.ChatFailure
import com.fuelingchat.service.ChatService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.serialization.ContentConvertException
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.json.Json
import kotlin.time.TimeSource

private val jsonFormat = Json { ignoreUnknownKeys = true }

private const val INVALID_BODY_MESSAGE = "Invalid request body"
private const val INTERNAL_ERROR_MESSAGE = "Internal server error"

/**
 * CONTRACT-API-001: the single route group POST /chat.
 * One request → one response (D-7). Parse → validate → delegate to ChatService → respond;
 * the route holds no business logic.
 */
fun Route.chatRoutes(chatServiceProvider: () -> ChatService) {
    install(ContentNegotiation) {
        json(jsonFormat)
    }

    route("/chat") {
        post {
            val started = TimeSource.Monotonic.markNow()
            val request = call.receive<ChatRequest>()
            validatePrompt(request.prompt)
            validateProvider(request.provider)
            val turn = chatServiceProvider().chat(request.prompt, request.fuelingId, request.provider)
            call.respond(
                HttpStatusCode.OK,
                ChatResponse(turn.summary, turn.provider, started.elapsedNow().inWholeMilliseconds),
            )
        }
    }
}

/**
 * CONTRACT-API-001 masked error table, installed at application level (StatusPages is an
 * application-level plugin in Ktor 3, so it cannot live inside a route group). Mounted by
 * AppModule.boot() and by the route tests — both share these exact handlers.
 */
fun Application.configureChatStatusPages() {
    install(StatusPages) {
        exception<ChatFailure> { call, failure ->
            call.respondMasked(failure.status, failure.maskedMessage)
        }
        exception<BadRequestException> { call, _ ->
            call.respondMasked(400, INVALID_BODY_MESSAGE)
        }
        exception<ContentConvertException> { call, _ ->
            call.respondMasked(400, INVALID_BODY_MESSAGE)
        }
        exception<ContentTransformationException> { call, _ ->
            call.respondMasked(400, INVALID_BODY_MESSAGE)
        }
        exception<Throwable> { call, _ ->
            call.respondMasked(500, INTERNAL_ERROR_MESSAGE)
        }
    }
}

/**
 * StatusPages responds on the application call, outside the route-scoped ContentNegotiation,
 * so masked error bodies are serialized here as-is to keep the frozen {"error": ...} contract.
 */
private suspend fun ApplicationCall.respondMasked(
    status: Int,
    message: String,
) {
    respond(
        HttpStatusCode.fromValue(status),
        TextContent(jsonFormat.encodeToString(ErrorResponse(message)), ContentType.Application.Json),
    )
}

private fun validatePrompt(prompt: String) {
    if (prompt.isBlank()) throw ChatFailure(400, "Prompt must not be blank")
}

private fun validateProvider(provider: String?) {
    if (provider == null || provider == "local" || provider == "remote") return
    throw ChatFailure(400, "Invalid provider")
}
