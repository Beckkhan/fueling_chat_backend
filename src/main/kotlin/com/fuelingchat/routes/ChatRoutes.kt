package com.fuelingchat.routes

import com.fuelingchat.service.ChatFailure
import com.fuelingchat.service.ChatFailureStatus
import com.fuelingchat.service.ChatService
import com.fuelingchat.service.ChatServiceRequest
import com.fuelingchat.service.ChatTurn
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

/**
 * T11: single route group POST /chat per CONTRACT-API-001/002. One request -> one response,
 * no streaming primitives anywhere (D-7). The route holds no business logic:
 * parse -> validate -> delegate to [ChatService] -> respond.
 *
 * Installs ContentNegotiation (kotlinx-json) exactly once — no other module may install it again.
 *
 * @param chatServiceProvider lazy resolver so the T11 placeholder DI binding never fails at boot;
 *   resolves [ChatService] only when POST /chat arrives. T12 keeps this wiring unchanged.
 */
fun Application.chatRoutes(chatServiceProvider: () -> ChatService) {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            },
        )
    }
    routing {
        post("/chat") {
            call.handleChat(chatServiceProvider)
        }
    }
}

private suspend fun ApplicationCall.handleChat(chatServiceProvider: () -> ChatService) {
    val entryMillis = System.currentTimeMillis()
    val request = receiveValidRequest()
    val turn = request?.let { runTurn(it, chatServiceProvider) }
    if (turn != null) {
        val elapsedMs = (System.currentTimeMillis() - entryMillis).coerceAtLeast(0L)
        respond(HttpStatusCode.OK, ChatResponse(turn.summary, turn.provider, elapsedMs))
    }
}

/**
 * Parses and validates the body; responds a masked 400 and returns null when the request is invalid.
 */
private suspend fun ApplicationCall.receiveValidRequest(): ChatRequest? {
    val request =
        try {
            receive<ChatRequest>()
        } catch (_: SerializationException) {
            null
        } catch (_: ContentTransformationException) {
            null
        } catch (_: BadRequestException) {
            null
        }
    val validationError = request?.validationError()
    if (request != null && validationError == null) {
        return request
    }
    respondMaskedError(HttpStatusCode.BadRequest, validationError ?: MASKED_MALFORMED_JSON)
    return null
}

/**
 * Runs the turn; responds the mapped masked failure and returns null when it fails,
 * keeping [CancellationException] propagating to the caller.
 */
private suspend fun ApplicationCall.runTurn(
    request: ChatRequest,
    chatServiceProvider: () -> ChatService,
): ChatTurn? {
    val serviceRequest = ChatServiceRequest(request.prompt, request.fuelingId, request.effectiveProvider())
    return try {
        chatServiceProvider().run(serviceRequest)
    } catch (failure: ChatFailure) {
        respondMaskedFailure(failure)
        null
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        respondMaskedError(HttpStatusCode.InternalServerError, MASKED_INTERNAL_ERROR)
        null
    }
}

private suspend fun ApplicationCall.respondMaskedFailure(failure: ChatFailure) {
    val status =
        when (failure.status) {
            ChatFailureStatus.DATABASE -> HttpStatusCode.InternalServerError
            ChatFailureStatus.PROVIDER -> HttpStatusCode.BadGateway
        }
    respondMaskedError(status, failure.maskedMessage)
}

/**
 * Responds with [status] and body `ChatErrorResponse(maskedMessage)` — masked bodies only,
 * never SQL, credentials or raw provider errors.
 */
private suspend fun ApplicationCall.respondMaskedError(
    status: HttpStatusCode,
    maskedMessage: String,
) {
    respond(status, ChatErrorResponse(maskedMessage))
}

/** Generic masked message for unmapped service exceptions (CONTRACT-API-002 500 row). */
private const val MASKED_INTERNAL_ERROR = "Internal server error"

/** Masked message for malformed JSON bodies (CONTRACT-API-002 400 row). */
private const val MASKED_MALFORMED_JSON = "Invalid request body"
