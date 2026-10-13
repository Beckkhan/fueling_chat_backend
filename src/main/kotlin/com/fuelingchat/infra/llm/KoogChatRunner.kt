package com.fuelingchat.infra.llm

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistryBuilder
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel

/**
 * Shared single-turn Koog agent assembly (CONTRACT-LLM-001, D-16).
 *
 * Provider-parameterized by construction: the owning [LlmClient] builds the provider-specific
 * [PromptExecutor] (Ollama in T13, DeepSeek in T14) and [LLModel]; this class owns only the turn
 * mechanics — system prompt + history mapped by role, the service-supplied tool registry (D-8:
 * never empty, enforced in service), and the Koog agent run returning the final text.
 *
 * References only Koog types and [ChatMessage] from this package — never service classes.
 *
 * Owner: coder-implementer, Block 1. Cross-block interface consumed by T13 Block 2
 * (OllamaLlmClient) and reused by T14 — do not change these signatures.
 */
class KoogChatRunner(
    private val promptExecutor: PromptExecutor,
    private val model: LLModel,
) {
    /**
     * One Koog single-run turn.
     *
     * @param systemPrompt system instructions assembled by the service (D-6, D-9)
     * @param history last N dialog entries, role-mapped via [ChatMessage.ROLE_USER] /
     *   [ChatMessage.ROLE_ASSISTANT] (D-1, D-11)
     * @param userMessage the current user prompt
     * @param tools the service-supplied Koog tool list (D-8, never empty)
     * @return the agent's final summary text
     */
    suspend fun runTurn(
        systemPrompt: String,
        history: List<ChatMessage>,
        userMessage: String,
        tools: List<Tool<*, *>>,
    ): String {
        val seedPrompt =
            prompt("chat-turn") {
                system(systemPrompt)
                history.forEach { message ->
                    when (message.role) {
                        ChatMessage.ROLE_USER -> user(message.body)
                        ChatMessage.ROLE_ASSISTANT -> assistant(message.body)
                    }
                }
            }
        val toolRegistry = ToolRegistryBuilder().tools(tools).build()
        val agentConfig = AIAgentConfig(prompt = seedPrompt, model = model, maxAgentIterations = 50)
        val agent = AIAgent(promptExecutor, agentConfig, toolRegistry)
        return agent.run(userMessage)
    }
}
