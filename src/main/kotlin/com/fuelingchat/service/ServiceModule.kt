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
        // The named LlmClient bindings in clientModule stay error() stubs until T13/T14, so the
        // ProviderRouter provider below resolves lazily and is never touched at boot.
        // DiSmokeTest additions for the app graph land in T14.
        bind<ProviderRouter>() with
            singleton {
                ProviderRouter(local = instance(tag = "local"), remote = instance(tag = "remote"))
            }
        bind<ChatService>() with
            singleton {
                DefaultChatService(
                    history = instance(),
                    historyLimit = instance<AppConfig>().historyLimit,
                    router = instance(),
                    getFuelingInfoTool = instance(),
                )
            }
    }
