package com.fuelingchat.infra.llm

import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton

val clientModule =
    DI.Module("client") {
        bind<LlmClient>(tag = "local") with singleton { error("LlmClient 'local' lands with the D-16 branch task") }
        bind<LlmClient>(tag = "remote") with singleton { error("LlmClient 'remote' lands with the D-16 branch task") }
    }
