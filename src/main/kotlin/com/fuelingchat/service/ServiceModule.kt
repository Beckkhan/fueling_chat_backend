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
        // T11 seam placeholder (integrator-owned): the route resolves ChatService lazily, so this
        // binding never fails at boot; the real implementation lands in T12.
        // TODO(T12): replace this placeholder with the real ChatService binding.
        bind<ChatService>() with singleton { error("ChatService implementation lands in T12") }
    }
