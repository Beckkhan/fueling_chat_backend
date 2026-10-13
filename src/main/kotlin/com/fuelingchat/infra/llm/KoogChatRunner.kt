package com.fuelingchat.infra.llm

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistryBuilder
import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel

/**
 * Shared Koog single-run turn assembly (CONTRACT-LLM-001, D-8/D-16): builds the turn prompt
 * (system prompt + dialog history mapped by role) and the tool registry from the
 * service-supplied tool set, then runs one agent turn and returns the final text.
 * Reused by OllamaLlmClient (T13) and DeepSeekLlmClient (T14); references only Koog types.
 */
class KoogChatRunner(
    private val promptExecutor: PromptExecutor,
    private val model: LLModel,
) : LlmClient {
    override suspend fun run(
        systemPrompt: String,
        history: List<ChatMessage>,
        userMessage: String,
        tools: List<Tool<*, *>>,
    ): String {
        val agent =
            AIAgent
                .builder()
                .promptExecutor(promptExecutor)
                .agentConfig(turnConfig(systemPrompt, history))
                .toolRegistry(ToolRegistryBuilder().tools(tools).build())
                .build()
        return agent.run(userMessage)
    }

    private fun turnConfig(
        systemPrompt: String,
        history: List<ChatMessage>,
    ): AIAgentConfig =
        AIAgentConfig(
            prompt =
                prompt(id = TURN_PROMPT_ID) {
                    system(systemPrompt)
                    history.forEach { appendHistoryMessage(it) }
                },
            model = model,
            maxAgentIterations = MAX_AGENT_ITERATIONS,
        )

    private fun PromptBuilder.appendHistoryMessage(message: ChatMessage) {
        when (message.role) {
            ChatMessage.ROLE_USER -> user(message.body)
            ChatMessage.ROLE_ASSISTANT -> assistant(message.body)
            else -> Unit
        }
    }

    companion object {
        private const val TURN_PROMPT_ID = "fueling-chat-turn"
        private const val MAX_AGENT_ITERATIONS = 50
    }
}
