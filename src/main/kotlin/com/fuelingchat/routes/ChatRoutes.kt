package com.fuelingchat.routes

import com.fuelingchat.service.ChatFailure
import com.fuelingchat.service.ChatService
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private const val DEFAULT_PROVIDER = "local"
private const val BLANK_PROMPT_MESSAGE = "prompt must not be blank"
private const val INVALID_PROVIDER_MESSAGE = "provider must be \"local\" or \"remote\""
private const val INVALID_BODY_MESSAGE = "invalid request body"
private const val INTERNAL_ERROR_MESSAGE = "internal server error"

fun Application.configureChat(chatService: ChatService) {
    install(ContentNegotiation) {
        json()
    }
    routing {
        route("/chat") {
            post {
                val started = TimeSource.Monotonic.markNow()
                try {
                    call.handleChat(chatService, started)
                } catch (invalid: InvalidRequest) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(invalid.message ?: INVALID_BODY_MESSAGE))
                } catch (failure: ChatFailure) {
                    call.respond(HttpStatusCode.fromValue(failure.status), ErrorResponse(failure.maskedMessage))
                } catch (_: BadRequestException) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(INVALID_BODY_MESSAGE))
                } catch (_: SerializationException) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(INVALID_BODY_MESSAGE))
                } catch (_: ContentTransformationException) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(INVALID_BODY_MESSAGE))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(INTERNAL_ERROR_MESSAGE))
                }
            }
        }
    }
}

private suspend fun ApplicationCall.handleChat(
    chatService: ChatService,
    started: TimeMark,
) {
    val request = receive<ChatRequest>()
    requirePromptNotBlank(request.prompt)
    val provider = validatedProvider(request.provider)
    val turn = chatService.chat(request.prompt, request.fuelingId, provider)
    val elapsedMs = started.elapsedNow().inWholeMilliseconds
    respond(HttpStatusCode.OK, ChatResponse(turn.summary, turn.provider, elapsedMs))
}

private fun requirePromptNotBlank(prompt: String) {
    if (prompt.isBlank()) throw InvalidRequest(BLANK_PROMPT_MESSAGE)
}

private fun validatedProvider(provider: String?): String {
    if (provider == null) return DEFAULT_PROVIDER
    if (provider == "local" || provider == "remote") return provider
    throw InvalidRequest(INVALID_PROVIDER_MESSAGE)
}

private class InvalidRequest(
    message: String,
) : RuntimeException(message)
