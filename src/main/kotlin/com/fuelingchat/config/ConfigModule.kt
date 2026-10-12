package com.fuelingchat.config

import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import java.nio.file.Path

fun configModule(envFile: Path = Path.of(".env")) =
    DI.Module("config") {
        bind<AppConfig>() with singleton { AppConfig(envFile) }
    }
