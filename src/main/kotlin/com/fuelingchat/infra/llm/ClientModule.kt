package com.fuelingchat.infra.llm

import com.fuelingchat.config.AppConfig
import com.fuelingchat.infra.logging.LoggerProvider
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton

val clientModule =
    DI.Module("client") {
        bind<LlmClient>(tag = "local") with
            singleton {
                val config: AppConfig = instance()
                OllamaLlmClient(
                    config,
                    llmWireLoggingFactory(
                        provider = LOCAL_PROVIDER,
                        model = config.ollamaModel,
                        logger = instance<LoggerProvider>().logger(WIRE_LOGGER_NAME),
                    ),
                )
            }
        bind<LlmClient>(tag = "remote") with
            singleton {
                val config: AppConfig = instance()
                DeepSeekLlmClient(
                    config,
                    llmWireLoggingFactory(
                        provider = REMOTE_PROVIDER,
                        model = config.deepseekModel,
                        logger = instance<LoggerProvider>().logger(WIRE_LOGGER_NAME),
                    ),
                )
            }
    }
