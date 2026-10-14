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

        // Consumed by ProviderRouter (ServiceModule) as instance(tag = "local"/"remote").
        // T14 (integrator-owned): both named bindings are real clients — no LlmClient stub remains.
        bind<LlmClient>(tag = "local") with singleton { OllamaLlmClient(instance(), instance()) }
        bind<LlmClient>(tag = "remote") with singleton { DeepSeekLlmClient(instance(), instance()) }
    }
