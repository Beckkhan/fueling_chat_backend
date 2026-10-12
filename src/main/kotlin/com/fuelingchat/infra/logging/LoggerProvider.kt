package com.fuelingchat.infra.logging

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

class LoggerProvider {
    fun logger(name: String): KLogger = KotlinLogging.logger(name)
}
