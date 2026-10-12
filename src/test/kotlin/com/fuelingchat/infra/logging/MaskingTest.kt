package com.fuelingchat.infra.logging

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class MaskingTest {
    private val logger: org.slf4j.Logger = LoggerFactory.getLogger("com.fuelingchat.infra.logging.MaskingTest")
    private lateinit var appender: ListAppender<ILoggingEvent>

    @BeforeEach
    fun attachAppender() {
        appender = ListAppender()
        appender.start()
        rootLogger().addAppender(appender)
    }

    @AfterEach
    fun detachAppender() {
        rootLogger().detachAppender(appender)
        appender.stop()
    }

    @Test
    fun dbPasswordIsMaskedBeforeWrite() {
        logger.info("connecting with DB_PASSWORD=hunter2")

        assertTrue(recordedMessages().contains("DB_PASSWORD=***"))
        assertFalse(recordedMessages().contains("hunter2"))
    }

    @Test
    fun deepseekApiKeyIsMaskedBeforeWrite() {
        logger.info("using DEEPSEEK_API_KEY=sk-test-12345 for the remote provider")

        assertTrue(recordedMessages().contains("DEEPSEEK_API_KEY=***"))
        assertFalse(recordedMessages().contains("sk-test-12345"))
    }

    @Test
    fun authorizationBearerIsMaskedBeforeWrite() {
        logger.info("Authorization: Bearer tok_abc123xyz")

        assertTrue(recordedMessages().contains("Authorization: Bearer ***"))
        assertFalse(recordedMessages().contains("tok_abc123xyz"))
    }

    @Test
    fun jsonSecretFieldsAreMaskedBeforeWrite() {
        logger.info("""body {"api_key": "ak-123", "password": "pw-456", "secret": "sec-789", "token": "tok-000"}""")

        assertTrue(recordedMessages().contains(""""api_key": "***""""))
        assertTrue(recordedMessages().contains(""""password": "***""""))
        assertTrue(recordedMessages().contains(""""secret": "***""""))
        assertTrue(recordedMessages().contains(""""token": "***""""))
        assertFalse(recordedMessages().contains("ak-123"))
        assertFalse(recordedMessages().contains("pw-456"))
        assertFalse(recordedMessages().contains("sec-789"))
        assertFalse(recordedMessages().contains("tok-000"))
    }

    @Test
    fun innocentMessagesPassThroughUnchanged() {
        logger.info("fueling F-10001 completed normally")

        assertTrue(recordedMessages().contains("fueling F-10001 completed normally"))
    }

    private fun recordedMessages(): String = appender.list.joinToString(separator = "\n") { it.formattedMessage }

    private fun rootLogger(): Logger =
        (LoggerFactory.getILoggerFactory() as LoggerContext).getLogger(Logger.ROOT_LOGGER_NAME)
}
