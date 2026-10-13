package com.fuelingchat.infra.llm

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.ktor.KtorKoogHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton

val clientModule =
    DI.Module("client") {
        bind<LlmClient>(tag = "local") with singleton { OllamaLlmClient(instance(), koogHttpClientFactory()) }
        bind<LlmClient>(tag = "remote") with singleton { error("LlmClient 'remote' lands with the D-16 branch task") }
    }

// Apache5 3.3.3 is a transitive dependency of Koog's http-client-ktor, so no production dependency
// change is needed; the MockEngine-backed factory is injected directly in OllamaLlmClientTest.
private fun koogHttpClientFactory(): KoogHttpClient.Factory =
    KtorKoogHttpClient.Factory(baseClient = HttpClient(Apache5))
