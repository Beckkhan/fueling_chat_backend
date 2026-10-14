package com.fuelingchat.infra.llm

import ai.koog.agents.core.tools.Tool
import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import com.fuelingchat.config.AppConfig

/**
 * The `local` provider of the pinned [LlmClient] seam (D-16): Ollama over the Koog single-run turn
 * assembly in [KoogChatRunner].
 *
 * The transport arrives as a [KoogHttpClient.Factory] so production resolves the default Ktor factory
 * while tests inject a MockEngine-backed one — the seam is exercised without a live Ollama (D-8 tools
 * and the whole turn flow included).
 */
class OllamaLlmClient(
    config: AppConfig,
    httpClientFactory: KoogHttpClient.Factory,
) : LlmClient {
    private val runner =
        KoogChatRunner(
            koogClient = OllamaClient(httpClientFactory = httpClientFactory, baseUrl = config.ollamaBaseUrl),
            model = LLModel(LLMProvider.Ollama, config.ollamaModel),
        )

    override suspend fun run(
        systemPrompt: String,
        history: List<ChatMessage>,
        userMessage: String,
        tools: List<Tool<*, *>>,
    ): String = runner.runTurn(systemPrompt, history, userMessage, tools)
}
