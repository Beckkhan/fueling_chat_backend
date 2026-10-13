package com.fuelingchat.infra.llm

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistryBuilder
import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel

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
            AIAgent(
                promptExecutor = promptExecutor,
                agentConfig = turnConfig(systemPrompt, history),
                toolRegistry = ToolRegistryBuilder().tools(tools).build(),
            )
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
            USER_ROLE -> user(message.body)
            ASSISTANT_ROLE -> assistant(message.body)
        }
    }

    companion object {
        private const val TURN_PROMPT_ID = "fueling-chat-turn"
        private const val MAX_AGENT_ITERATIONS = 50
        private const val USER_ROLE = "user"
        private const val ASSISTANT_ROLE = "assistant"
    }
}
