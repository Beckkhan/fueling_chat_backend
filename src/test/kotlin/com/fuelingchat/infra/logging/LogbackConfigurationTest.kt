package com.fuelingchat.infra.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class LogbackConfigurationTest {
    private val context: LoggerContext = LoggerFactory.getILoggerFactory() as LoggerContext

    @Test
    fun logbackXmlIsPickedUpAutomatically() {
        val console = context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("CONSOLE")

        assertNotNull(console)
        assertTrue(console is ConsoleAppender<ILoggingEvent>)
    }

    @Test
    fun appPackagesDefaultToInfo() {
        assertEquals(Level.INFO, context.getLogger(Logger.ROOT_LOGGER_NAME).level)
        assertEquals(Level.INFO, context.getLogger("com.fuelingchat").level)
    }

    @Test
    fun maskingFilterCoversEveryAppender() {
        assertTrue(context.turboFilterList.any { it is MaskingTurboFilter })
    }
}
