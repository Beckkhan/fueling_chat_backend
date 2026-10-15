package com.fuelingchat.routes

import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS

/**
 * Browser CORS policy for the chat API (D-13): the frontend calls POST /chat from its own origin, and a JSON
 * content-type makes every request preflighted. The API is unauthenticated and uses no cookies, so any origin
 * is allowed. Methods are Ktor's CORS defaults (GET/POST/HEAD); allowing [HttpHeaders.ContentType] is what
 * admits the `application/json` content type, which is not a CORS-safelisted value.
 */
fun Application.installCors() {
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
    }
}
