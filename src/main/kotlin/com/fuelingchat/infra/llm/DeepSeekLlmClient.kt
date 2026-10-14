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
 * The `remote` named binding of the pinned [LlmClient] seam (T14, CONTRACT-LLM-002): DeepSeek via
 * the pinned Koog surface (D-16).
 *
 * Surface verified against the pinned jars at scaffold time (see docs/tasks/T14.md, "Scaffold"):
 * - The DeepSeek client ships in a SEPARATE artifact `ai.koog:prompt-executor-deepseek-client-jvm`
 *   (1.3.0-beta; koog-agents-jvm 1.3.0 does not pull it) and `PromptExecutorBuilder` in Koog 1.3.0
 *   has NO `.deepSeek(...)` method — the client is registered through `addClient(...)`.
 * - `DeepSeekLLMClient(apiKey, settings = DeepSeekClientSettings(), httpClientFactory, ...)`: the
 *   base URL lives inside `DeepSeekClientSettings(baseUrl = "https://api.deepseek.com")` — D-16's
 *   "(apiKey, baseUrl = ...)" wording is a deviation, documented for the correctness review.
 *   `httpClientFactory` is REQUIRED (no default) — the same injectable seam as T13.
 * - Auth: the factory-created client sends `Authorization: Bearer <api_key>` on every request
 *   (AbstractOpenAILLMClient.createConfiguredHttpClient) and posts to `<baseUrl>/chat/completions`
 *   with the OpenAI-compatible wire format.
 *
 * [httpClientFactory] is the injectable seam (KoogHttpClient.Factory): production wires
 * KtorKoogHttpClient.Factory(); tests pass KtorKoogHttpClient.Factory(HttpClient(MockEngine { ... })).
 *
 * Owner: coder-implementer, Block 1. Constructed by Block 2's MockEngine test and by the
 * integrator's `remote` ClientModule binding.
 */
class DeepSeekLlmClient(
    private val config: AppConfig,
    private val httpClientFactory: KoogHttpClient.Factory,
) : LlmClient {
    // Built once at construction: Koog's DeepSeekLLMClient is registered on the pinned builder via
    // addClient — there is NO `.deepSeek(...)` builder method in Koog 1.3.0. DeepSeekClientSettings
    // defaults (baseUrl = "https://api.deepseek.com", chatCompletionsPath = "chat/completions")
    // match the DeepSeek API base, so no AppConfig base-URL key is needed.
    private val promptExecutor: PromptExecutor =
        PromptExecutor
            .builder()
            .addClient(
                DeepSeekLLMClient(
                    apiKey = config.deepseekApiKey,
                    httpClientFactory = httpClientFactory,
                ),
            ).build()

    // Provider-specific model from config — LLMProvider.DeepSeek exists in the pinned
    // prompt-llm 1.3.0. Capabilities are REQUIRED on this path: the OpenAI-base chat path calls
    // model.requireCapability(Completion) and requireCapability(Tools) (tools are always sent,
    // D-8), and LLModel.supports() is false for null capabilities — an unset list fails every run.
    private val model: LLModel =
        LLModel(
            provider = LLMProvider.DeepSeek,
            id = config.deepseekModel,
            capabilities = listOf(LLMCapability.Completion, LLMCapability.Tools),
        )

    // Shared turn assembly from T13 (unchanged, reused as-is — no duplicated run logic).
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
