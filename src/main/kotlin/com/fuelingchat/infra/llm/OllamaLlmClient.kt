package com.fuelingchat.infra.llm

import ai.koog.agents.core.tools.Tool
import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import com.fuelingchat.config.AppConfig

/**
 * The `local` named binding of the pinned [LlmClient] seam (T13, CONTRACT-LLM-001): Ollama via
 * the pinned Koog 1.3.0 surface (D-16).
 *
 * Verified against the pinned jars: Koog's OllamaClient posts to `<baseUrl>/api/chat` — the
 * Ollama native endpoint, NOT the OpenAI-compatible `/v1/chat/completions` (wording deviation
 * from the T13 description, documented for the correctness review) — and its
 * DEFAULT_BASE_URL is `http://localhost:11434` with no `/v1` suffix. The integrator corrects
 * AppConfig's OLLAMA_BASE_URL default and the tracked `.env.example` line to match.
 *
 * [httpClientFactory] is the injectable seam (KoogHttpClient.Factory): production wires
 * KtorKoogHttpClient.Factory(); tests pass KtorKoogHttpClient.Factory(HttpClient(MockEngine { ... })).
 *
 * Owner: coder-implementer, Block 2.
 */
class OllamaLlmClient(
    private val config: AppConfig,
    private val httpClientFactory: KoogHttpClient.Factory,
) : LlmClient {
    // Built once at construction: PromptExecutorBuilder.ollama defaults timeout/clock/context-window/
    // tool-schema params, and the inner OllamaClient posts to <baseUrl>/api/chat.
    private val promptExecutor: PromptExecutor =
        PromptExecutor
            .builder()
            .ollama(baseUrl = config.ollamaBaseUrl, httpClientFactory = httpClientFactory)
            .build()

    // Provider-specific model from config (LLModel capabilities/context defaults stay null).
    private val model: LLModel = LLModel(provider = LLMProvider.Ollama, id = config.ollamaModel)

    // Shared turn assembly (Block 1) — reused by T14's DeepSeek client.
    private val runner: KoogChatRunner = KoogChatRunner(promptExecutor = promptExecutor, model = model)

    /**
     * Pinned LlmClient seam (spec §3) — this signature matches it exactly and must not change.
     * Delegates the whole turn to [KoogChatRunner.runTurn].
     */
    override suspend fun run(
        systemPrompt: String,
        history: List<ChatMessage>,
        userMessage: String,
        tools: List<Tool<*, *>>,
    ): String =
        runner.runTurn(
            systemPrompt = systemPrompt,
            history = history,
            userMessage = userMessage,
            tools = tools,
        )
}
