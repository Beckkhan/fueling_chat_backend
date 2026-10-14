package com.fuelingchat.infra.llm

import ai.koog.agents.core.tools.Tool
import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekClientSettings
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import com.fuelingchat.config.AppConfig

/**
 * The `remote` named binding of the pinned [LlmClient] seam (T14, CONTRACT-LLM-002): DeepSeek over the
 * pinned Koog 1.3.0 surface (D-16), with the turn assembly delegated to T13's shared [KoogChatRunner].
 *
 * Surface verified against the pinned jars (D-16):
 * - **Artifact:** the DeepSeek executor client is published separately as
 *   `ai.koog:prompt-executor-deepseek-client-jvm:1.3.0-beta`; `koog-agents-jvm:1.3.0` does NOT pull it.
 *   The dependency is an integrator-owned `build.gradle.kts` step, not part of this block.
 * - **No `baseUrl` constructor parameter** — D-16's `DeepSeekLLMClient(apiKey, baseUrl = …)` wording is a
 *   deviation. The base URL lives in `DeepSeekClientSettings(baseUrl = …)`, pinned explicitly below.
 * - `DeepSeekLLMClient(apiKey, settings, httpClientFactory, …)` requires the factory (no default); it is
 *   the same injectable [KoogHttpClient.Factory] seam T13 uses, so tests pass a MockEngine-backed factory.
 * - The client posts to `<baseUrl>/chat/completions` with `Authorization: Bearer <api key>` in the
 *   OpenAI-compatible wire format.
 * - **Capabilities are mandatory on this path.** The pinned `AbstractOpenAILLMClient.getResponse` calls
 *   `model.requireCapability(LLMCapability.Completion)` and, when tools are present,
 *   `requireCapability(LLMCapability.Tools)`; `LLModel.supports` is `false` for an unset capability list,
 *   so leaving them null fails every turn. Tools are always supplied (D-8), so both are declared here.
 *
 * References only Koog types, [ChatMessage] and [AppConfig] — never a service class. No credentials and no
 * provider response text are logged or echoed from this class.
 */
class DeepSeekLlmClient(
    config: AppConfig,
    httpClientFactory: KoogHttpClient.Factory,
) : LlmClient {
    // Built once at construction. Positional arguments: the runner's first parameter is the Koog client
    // seam it drives (see KoogChatRunner), the second the provider-specific model.
    private val runner: KoogChatRunner =
        KoogChatRunner(
            DeepSeekLLMClient(
                apiKey = config.deepseekApiKey,
                settings = DeepSeekClientSettings(baseUrl = DEEPSEEK_BASE_URL),
                httpClientFactory = httpClientFactory,
            ),
            LLModel(
                provider = LLMProvider.DeepSeek,
                id = config.deepseekModel,
                capabilities = listOf(LLMCapability.Completion, LLMCapability.Tools),
            ),
        )

    /** The pinned seam (spec §3) — this signature matches [LlmClient] exactly and must not change. */
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

    private companion object {
        /** The single DeepSeek API base (D-16); spelled out rather than relying on the Koog default. */
        private const val DEEPSEEK_BASE_URL = "https://api.deepseek.com"
    }
}
