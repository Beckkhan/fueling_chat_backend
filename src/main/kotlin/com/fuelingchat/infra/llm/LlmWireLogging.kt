package com.fuelingchat.infra.llm

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.ktor.KtorKoogHttpClient
import io.github.oshai.kotlinlogging.KLogger
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.HttpClientCall
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.CancellationException

/** Provider tag of the local (Ollama) client; mirrors `ProviderRouter.LOCAL` so infra never imports the service. */
const val LOCAL_PROVIDER = "local"

/** Provider tag of the remote (DeepSeek) client; mirrors `ProviderRouter.REMOTE`. */
const val REMOTE_PROVIDER = "remote"

/**
 * Logger name of the two wire lines. Must stay under `com.fuelingchat` — logback.xml raises `io.ktor`
 * to WARN, and the plugin instance's runtime class lives in the `io.ktor` package, so deriving the
 * name from the object would silently suppress every wire line at INFO.
 */
const val WIRE_LOGGER_NAME = "com.fuelingchat.infra.llm.LlmWireLogging"

/**
 * Ktor client plugin that logs the complete wire traffic of one LLM provider turn as JSON:
 *
 * - BEFORE the request is sent: `llm request: provider=<p>, model=<m>, body=<full wire JSON>`
 * - AFTER the response is received: `llm response: provider=<p>, model=<m>, elapsed_ms=<n>, body=<full wire JSON>`
 *
 * The hook runs inside the caller's coroutine, so both lines carry the request MDC (`correlation_id`)
 * of the chat turn. Bodies are logged in full, never truncated. Logback's MaskingTurboFilter still
 * masks any secret-shaped fields inside the logged JSON before it reaches the console.
 */
class LlmWireLoggingConfig {
    /** Provider tag of the turn: `local` or `remote`. */
    var provider: String = "unknown"

    /** Provider model id, e.g. `qwen3:8b` or `deepseek-v4-pro`. */
    var model: String = "unknown"

    /** Logger the two lines are written to. */
    var logger: KLogger? = null
}

val LlmWireLogging: ClientPlugin<LlmWireLoggingConfig> =
    createClientPlugin("LlmWireLogging", ::LlmWireLoggingConfig) {
        val provider = pluginConfig.provider
        val model = pluginConfig.model
        val logger = pluginConfig.logger
        on(Send) { request ->
            val activeLogger = logger ?: return@on proceed(request)
            val startedAt = System.nanoTime()
            val requestBody = request.wireBodyText()
            activeLogger.info { "llm request: provider=$provider, model=$model, body=$requestBody" }
            val call = proceed(request)
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            val responseBody = call.wireResponseBody()
            activeLogger.info {
                "llm response: provider=$provider, model=$model, elapsed_ms=$elapsedMs, body=$responseBody"
            }
            call
        }
    }

/**
 * Builds the Koog HTTP-client factory the `local`/`remote` [LlmClient]s run on, with [LlmWireLogging]
 * installed on the underlying Ktor client. [baseClient] lets tests inject a MockEngine-backed client;
 * production resolves the default engine, exactly like the Koog no-arg factory.
 */
fun llmWireLoggingFactory(
    provider: String,
    model: String,
    logger: KLogger,
    baseClient: HttpClient? = null,
): KoogHttpClient.Factory {
    val loggingClient =
        if (baseClient == null) {
            HttpClient { installLlmWireLogging(provider, model, logger) }
        } else {
            baseClient.config { installLlmWireLogging(provider, model, logger) }
        }
    return KtorKoogHttpClient.Factory(loggingClient)
}

private fun HttpClientConfig<*>.installLlmWireLogging(
    provider: String,
    model: String,
    logger: KLogger,
) {
    install(LlmWireLogging) {
        this.provider = provider
        this.model = model
        this.logger = logger
    }
}

/**
 * The complete response body as the provider sent it, or null when it cannot be re-read: a logging
 * failure must never break the chat turn, so the response line still carries provider/model/elapsed.
 */
@Suppress("SwallowedException", "TooGenericExceptionCaught")
private suspend fun HttpClientCall.wireResponseBody(): String? =
    try {
        response.bodyAsText()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        null
    }

/**
 * The complete request body as the engine will send it. The Send phase hands over the final
 * [OutgoingContent] after ContentNegotiation; channel-shaped bodies are read fully and replaced so
 * the wire send still carries the same bytes.
 */
private suspend fun HttpRequestBuilder.wireBodyText(): String? {
    val content = body as? OutgoingContent ?: return null
    return when (content) {
        is TextContent -> {
            content.text
        }

        is OutgoingContent.ByteArrayContent -> {
            content.bytes().decodeToString()
        }

        is OutgoingContent.ReadChannelContent -> {
            val text = content.readFrom().toByteArray().decodeToString()
            setBody(TextContent(text, contentType() ?: ContentType.Application.Json))
            text
        }

        // WriteChannelContent / multipart never occur on the pinned Koog chat endpoints; the line still
        // logs provider/model, with body=null.
        else -> {
            null
        }
    }
}
