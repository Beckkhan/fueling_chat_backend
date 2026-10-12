package com.fuelingchat.infra.logging

import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton

val loggingModule =
    DI.Module("logging") {
        bind<LoggerProvider>() with singleton { LoggerProvider() }
    }
