package com.fuelingchat.infra.logging

import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.calllogging.processingTimeMillis
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.httpVersion
import io.ktor.server.request.path
import io.ktor.server.request.queryString
import io.ktor.server.request.userAgent
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.util.UUID

/**
 * Turbo-style per-request logging: one line per request in the turbo shape
 * `METHOD path?query VERSION STATUS DURATION USER_AGENT`, with the correlation id
 * (`X-Request-Id` header or a generated UUID) propagated to every log line of the call via MDC.
 * Health/probe paths are excluded.
 *
 * The request logger is pinned to [REQUEST_LOGGER_NAME] on purpose: CallLogging's default logger is
 * Ktor's `io.ktor.server.Application`, and logback silences `io.ktor` at WARN to drop the CORS handler
 * noise, which would silence the request lines too.
 */
fun Application.installRequestLogging() {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path() !in IGNORE_PATHS }
        callIdMdc(name = "correlation_id")
        mdc(name = "http_method") { call -> call.request.httpMethod.value }
        mdc(name = "http_status") { call ->
            call.response
                .status()
                ?.value
                .toString()
        }
        mdc(name = "http_version") { call -> call.request.httpVersion }
        mdc(name = "http_path") { call -> call.request.path() }
        mdc(name = "user_agent") { call -> call.request.userAgent() }
        mdc(name = "http_x_forwarded_for") { call ->
            call.request.header(HttpHeaders.XForwardedFor) ?: "127.0.0.1"
        }
        format { call ->
            val httpMethod = call.request.httpMethod.value
            val path = call.request.path()
            val query = call.request.queryString()
            val pathWithQuery = if (query.isEmpty()) path else "$path?$query"
            val version = call.request.httpVersion
            val status = call.response.status()?.value ?: 0
            val duration = call.processingTimeMillis()
            val userAgent = call.request.userAgent()
            "$httpMethod $pathWithQuery $version $status $duration $userAgent"
        }
        logger = LoggerFactory.getLogger(REQUEST_LOGGER_NAME)
    }

    install(CallId) {
        retrieve { call -> call.request.header(HttpHeaders.XRequestId) ?: UUID.randomUUID().toString() }
    }
}

private const val REQUEST_LOGGER_NAME = "com.fuelingchat.request"

private val IGNORE_PATHS = setOf("/metrics", "/readiness", "/liveness", "/health-check", "/info", "/time")
