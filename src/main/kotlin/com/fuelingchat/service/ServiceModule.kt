package com.fuelingchat.service

import com.fuelingchat.service.tool.GetFuelingInfoTool
import com.fuelingchat.service.tool.ToolDescriptorLoader
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton

val serviceModule =
    DI.Module("service") {
        bind<GetFuelingInfoTool>() with singleton { GetFuelingInfoTool(ToolDescriptorLoader().load(), instance()) }
        bind<ChatService>() with singleton { error("ChatService lands with T12") }
    }
