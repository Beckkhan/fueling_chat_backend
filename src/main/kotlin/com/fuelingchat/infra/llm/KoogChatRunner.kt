package com.fuelingchat.infra.llm

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistryBuilder
import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ROLE_ASSISTANT = "assistant"
private const val TURN_PROMPT_ID = "chat"
private const val MAX_AGENT_ITERATIONS = 10

/**
 * Shared Koog turn assembly for the pinned [LlmClient] seam (D-16).
 *
 * One single-run agent is built per turn from the service-supplied system prompt, dialog history
 * (D-1/D-11) and tool set (D-8) over the injected Koog client and model. No business state is held, so
 * the `local` (Ollama) and `remote` (DeepSeek) clients differ only in the [LLMClient] and [LLModel]
 * they inject and run the exact same turn logic.
 */
class KoogChatRunner(
    private val koogClient: LLMClient,
    private val model: LLModel,
) {
    private val promptExecutor = MultiLLMPromptExecutor(koogClient)

    /** Runs one turn and returns the final text of the agent run (single response per turn, D-7). */
    suspend fun runTurn(
        systemPrompt: String,
        history: List<ChatMessage>,
        userMessage: String,
        tools: List<Tool<*, *>>,
    ): String {
        val toolRegistry = ToolRegistryBuilder().tools(tools).build()
        val agent =
            AIAgent(
                promptExecutor = promptExecutor,
                agentConfig = turnConfig(systemPrompt, history),
                toolRegistry = toolRegistry,
            )
        return withContext(Dispatchers.IO) {
            agent.run(userMessage)
        }
    }

    private fun turnConfig(
        systemPrompt: String,
        history: List<ChatMessage>,
    ): AIAgentConfig {
        val turnPrompt =
            prompt(id = TURN_PROMPT_ID) {
                system(systemPrompt)
                history.forEach { appendHistoryMessage(it) }
            }
        return AIAgentConfig(
            prompt = turnPrompt,
            model = model,
            maxAgentIterations = MAX_AGENT_ITERATIONS,
        )
    }
}

/** Maps the journal roles (D-1) onto prompt roles; anything that is not our own reply counts as user input. */
private fun PromptBuilder.appendHistoryMessage(message: ChatMessage) {
    if (message.role == ROLE_ASSISTANT) {
        assistant(message.body)
    } else {
        user(message.body)
    }
}
