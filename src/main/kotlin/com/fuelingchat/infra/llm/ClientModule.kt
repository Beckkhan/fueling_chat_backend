package com.fuelingchat.infra.llm

import ai.koog.http.client.ktor.KtorKoogHttpClient
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton

val clientModule =
    DI.Module("client") {
        bind<LlmClient>(tag = "local") with singleton { OllamaLlmClient(instance(), KtorKoogHttpClient.Factory()) }
        bind<LlmClient>(tag = "remote") with singleton { error("LlmClient 'remote' lands with the D-16 branch task") }
    }
