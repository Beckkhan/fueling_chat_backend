package com.fuelingchat.infra.llm

import ai.koog.agents.core.tools.Tool
import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import com.fuelingchat.config.AppConfig

class DeepSeekLlmClient(
    config: AppConfig,
    httpClientFactory: KoogHttpClient.Factory,
) : LlmClient {
    private val runner =
        KoogChatRunner(
            promptExecutor =
                PromptExecutor
                    .builder()
                    .addClient(
                        DeepSeekLLMClient(
                            apiKey = config.deepseekApiKey,
                            httpClientFactory = httpClientFactory,
                        ),
                    ).build(),
            model =
                LLModel(
                    provider = LLMProvider.DeepSeek,
                    id = config.deepseekModel,
                    capabilities = listOf(LLMCapability.Completion, LLMCapability.Tools),
                ),
        )

    override suspend fun run(
        systemPrompt: String,
        history: List<ChatMessage>,
        userMessage: String,
        tools: List<Tool<*, *>>,
    ): String = runner.run(systemPrompt, history, userMessage, tools)
}
