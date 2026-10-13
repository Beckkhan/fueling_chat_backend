package com.fuelingchat.infra.llm

import ai.koog.agents.core.tools.Tool
import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import com.fuelingchat.config.AppConfig

class OllamaLlmClient(
    config: AppConfig,
    httpClientFactory: KoogHttpClient.Factory,
) : LlmClient {
    private val runner =
        KoogChatRunner(
            promptExecutor =
                PromptExecutor
                    .builder()
                    .ollama(baseUrl = config.ollamaBaseUrl, httpClientFactory = httpClientFactory)
                    .build(),
            model = LLModel(provider = LLMProvider.Ollama, id = config.ollamaModel),
        )

    override suspend fun run(
        systemPrompt: String,
        history: List<ChatMessage>,
        userMessage: String,
        tools: List<Tool<*, *>>,
    ): String = runner.run(systemPrompt, history, userMessage, tools)
}
