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

        // All singletons stay lazy: resolving ChatService reaches the named LlmClient bindings, which are
        // still error() stubs until T13/T14, so boot must never resolve them.
        bind<ChatService>() with
            singleton {
                ChatServiceImpl(
                    instance(),
                    instance(),
                    instance(),
                    instance(tag = "local"),
                    instance(tag = "remote"),
                )
            }
        bind<ProviderRouter>() with singleton { ProviderRouter() }
        bind<DialogHistory>() with
            singleton {
                DialogHistory(
                    instance<AppConfig>().dialogHistoryFile,
                    instance<AppConfig>().historyLimit,
                )
            }
    }
