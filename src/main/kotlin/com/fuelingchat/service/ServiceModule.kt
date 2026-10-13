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
        bind<DialogHistory>() with singleton { DialogHistory(instance<AppConfig>().dialogHistoryFile) }
        bind<ProviderRouter>() with singleton { ProviderRouter(di) }
        bind<ChatService>() with
            singleton {
                ChatServiceImpl(
                    history = instance(),
                    router = instance(),
                    fuelingInfoTool = instance(),
                    historyLimit = instance<AppConfig>().historyLimit,
                )
            }
    }
