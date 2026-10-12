package com.fuelingchat.service.tool

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

@Serializable
data class GetFuelingInfoArgs(
    @property:LLMDescription("The fueling identifier to look up.")
    val fueling_id: String,
)
