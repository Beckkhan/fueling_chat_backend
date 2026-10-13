package com.fuelingchat.infra.llm

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.ktor.KtorKoogHttpClient
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton

val clientModule =
    DI.Module("client") {
        // T13 (integrator-owned): production HTTP-client factory for the Koog providers. The
        // Ollama client (T13) and the DeepSeek client (T14) share this untagged singleton.
        bind<KoogHttpClient.Factory>() with singleton { KtorKoogHttpClient.Factory() }

        // Consumed by ProviderRouter as instance(tag = "local") in ServiceModule.
        bind<LlmClient>(tag = "local") with singleton { OllamaLlmClient(instance(), instance()) }
        bind<LlmClient>(tag = "remote") with singleton { error("LlmClient 'remote' lands with the D-16 branch task") }
    }
