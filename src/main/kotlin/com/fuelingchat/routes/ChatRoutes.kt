package com.fuelingchat.routes

import com.fuelingchat.service.ChatFailure
import com.fuelingchat.service.ChatService
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.ContentConvertException
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private const val BLANK_PROMPT_MESSAGE = "prompt must not be blank"
private const val INVALID_PROVIDER_MESSAGE = "provider must be \"local\" or \"remote\""
private const val INVALID_BODY_MESSAGE = "invalid request body"
private const val INTERNAL_ERROR_MESSAGE = "internal server error"

private val VALID_PROVIDERS = setOf("local", "remote")

/**
 * Registers `POST /chat` (CONTRACT-API-001 / CONTRACT-API-002).
 *
 * Parses and validates the request, delegates the turn to [chatService] and responds with the masked
 * result. The service provider is invoked per request, so installing this route never resolves the DI
 * binding. No streaming: every request is answered with a single body (D-7).
 */
fun Route.chatRoutes(chatService: () -> ChatService): Route =
    route("/chat") {
        post { call.handleChat(chatService) }
    }

@Suppress("TooGenericExceptionCaught") // Last-resort masking layer: nothing unmasked may reach a response body.
private suspend fun ApplicationCall.handleChat(chatService: () -> ChatService) {
    val started = TimeSource.Monotonic.markNow()
    try {
        val request = receive<ChatRequest>()
        if (request.prompt.isBlank()) {
            respond(HttpStatusCode.BadRequest, ErrorBody(BLANK_PROMPT_MESSAGE))
            return
        }
        if (request.provider != null && request.provider !in VALID_PROVIDERS) {
            respond(HttpStatusCode.BadRequest, ErrorBody(INVALID_PROVIDER_MESSAGE))
            return
        }
        val turn = chatService().runTurn(request.prompt, request.fuelingId, request.provider)
        respond(HttpStatusCode.OK, ChatResponse(turn.summary, turn.provider, started.elapsedMs()))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        respondMasked(error)
    }
}

private fun TimeMark.elapsedMs(): Int = elapsedNow().inWholeMilliseconds.toInt()

private suspend fun ApplicationCall.respondMasked(error: Throwable) {
    when (error) {
        is ChatFailure -> respond(HttpStatusCode.fromValue(error.status), ErrorBody(error.maskedMessage))

        is BadRequestException,
        is ContentTransformationException,
        is ContentConvertException,
        is SerializationException,
        -> respond(HttpStatusCode.BadRequest, ErrorBody(INVALID_BODY_MESSAGE))

        else -> respond(HttpStatusCode.InternalServerError, ErrorBody(INTERNAL_ERROR_MESSAGE))
    }
}
