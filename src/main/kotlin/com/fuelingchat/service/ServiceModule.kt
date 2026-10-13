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
        // TODO: placeholder binding — T12 replaces this with the real ChatService construction
        bind<ChatService>() with singleton { error("ChatService lands with T12") }
    }
