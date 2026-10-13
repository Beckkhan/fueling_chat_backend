package com.fuelingchat.service

import com.fuelingchat.config.AppConfig
import com.fuelingchat.service.tool.GetFuelingInfoTool
import com.fuelingchat.service.tool.ToolDescriptorLoader
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton

val serviceModule =
    DI.Module("service") {
        bind<GetFuelingInfoTool>() with singleton { GetFuelingInfoTool(ToolDescriptorLoader().load(), instance()) }

        bind<DialogHistory>() with
            singleton {
                DialogHistory(
                    journalFile = instance<AppConfig>().dialogHistoryFile,
                    historyLimit = instance<AppConfig>().historyLimit,
                )
            }

        // The named LlmClient bindings stay error() stubs until T13/T14; instance(tag = ...)
        // resolves lazily when this singleton is first created, so boot is unaffected.
        bind<ProviderRouter>() with
            singleton {
                ProviderRouter(local = instance(tag = "local"), remote = instance(tag = "remote"))
            }

        bind<ChatService>() with
            singleton {
                ChatServiceImpl(
                    history = instance(),
                    historyLimit = instance<AppConfig>().historyLimit,
                    router = instance(),
                    getFuelingInfoTool = instance(),
                )
            }
    }
