package com.fuelingchat.infra.llm

import ai.koog.agents.core.tools.Tool
import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import com.fuelingchat.config.AppConfig

/**
 * `remote` named binding (T14): DeepSeek via the Koog `ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient`.
 * Same injectable `KoogHttpClient.Factory` constructor parameter and same `run` contract as
 * `OllamaLlmClient` (T13); the whole turn assembly is delegated to the shared `KoogChatRunner` —
 * no duplicated run logic. `infrastructure.llm` stays business-free (references only Koog + AppConfig).
 *
 * Surface verified against the pinned jars at scaffold time (D-16) — wording deviations to
 * document in the correctness review:
 *  1. D-16 wording says `DeepSeekLLMClient(apiKey, baseUrl = "https://api.deepseek.com")`. The
 *     actual 1.3.0-beta constructor is
 *     `DeepSeekLLMClient(apiKey, settings = DeepSeekClientSettings(), httpClientFactory, ...)`:
 *     the base URL is carried by `DeepSeekClientSettings(baseUrl = "https://api.deepseek.com",
 *     chatCompletionsPath = "chat/completions", modelsPath = "models", timeoutConfig)`, not by a
 *     direct constructor parameter.
 *  2. `PromptExecutorBuilder` (pinned 1.3.0) has NO `deepSeek(...)` member (only
 *     `ollama`/`openAI`/`anthropic`/`addClient`/`fallback`/`build`); the DeepSeek client must be
 *     registered via `addClient(...)`, the same mechanism `.ollama(...)` uses internally.
 *  3. Koog's DeepSeek client module is published only as `1.3.0-beta` (see build.gradle.kts).
 *  4. Bearer auth verified in `AbstractOpenAILLMClient.createConfiguredHttpClient`: the factory is
 *     configured with `headers = mapOf("Authorization" to "Bearer <apiKey>")` via Ktor
 *     `defaultRequest`, so every outgoing request carries `Authorization: Bearer <DEEPSEEK_API_KEY>`.
 *     The outgoing request is `POST https://api.deepseek.com/chat/completions`.
 */
class DeepSeekLlmClient(
    config: AppConfig,
    httpClientFactory: KoogHttpClient.Factory,
) : LlmClient {
    // The capabilities list is REQUIRED: AbstractOpenAILLMClient.requireCapability throws when
    // capabilities are absent (supports() is false for null); it demands LLMCapability.Completion on
    // every turn and LLMCapability.Tools whenever tools are non-empty (KoogChatRunner always passes
    // the non-empty tool set, D-8).
    private val runner: KoogChatRunner =
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
