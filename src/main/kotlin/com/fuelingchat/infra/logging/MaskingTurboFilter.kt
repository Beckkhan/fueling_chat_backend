package com.fuelingchat.infra.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.turbo.TurboFilter
import ch.qos.logback.core.spi.FilterReply
import org.slf4j.Marker
import org.slf4j.helpers.MessageFormatter

class MaskingTurboFilter : TurboFilter() {
    override fun decide(
        marker: Marker?,
        logger: Logger,
        level: Level,
        format: String?,
        params: Array<Any?>?,
        throwable: Throwable?,
    ): FilterReply {
        val formatted = format?.let { MessageFormatter.arrayFormat(it, params).message } ?: return FilterReply.NEUTRAL
        val masked = SecretMasker.mask(formatted)
        return if (masked == formatted) {
            FilterReply.NEUTRAL
        } else {
            logger.log(marker, MASKING_FQCN, Level.toLocationAwareLoggerInteger(level), masked, null, throwable)
            FilterReply.DENY
        }
    }

    private companion object {
        const val MASKING_FQCN = "com.fuelingchat.infra.logging.MaskingTurboFilter"
    }
}
