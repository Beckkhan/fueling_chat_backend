package com.fuelingchat.infra.llm

import ai.koog.agents.core.tools.Tool

interface LlmClient {
    suspend fun run(
        systemPrompt: String,
        history: List<ChatMessage>,
        userMessage: String,
        tools: List<Tool<*, *>>,
    ): String
}
