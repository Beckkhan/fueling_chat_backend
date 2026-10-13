package com.fuelingchat.infra.llm

import ai.koog.agents.core.tools.Tool
import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import com.fuelingchat.config.AppConfig

/**
 * `local` named binding (T13): Ollama via the Koog 1.3.0 OllamaClient. The Koog HTTP-client
 * factory is an injectable constructor parameter so tests can pass a MockEngine-backed factory.
 * Verified against the pinned jars: Koog 1.3.0's OllamaClient speaks the native Ollama protocol
 * (POST {base}/api/chat), so the base URL must NOT carry a /v1 suffix (D-16 wording deviation,
 * documented in the correctness review).
 */
class OllamaLlmClient(
    config: AppConfig,
    httpClientFactory: KoogHttpClient.Factory,
) : LlmClient {
    private val runner: KoogChatRunner =
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
