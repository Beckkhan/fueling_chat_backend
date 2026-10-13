package com.fuelingchat.service

import ai.koog.agents.core.agent.exception.AIAgentException
import ai.koog.http.client.KoogHttpClientException
import ai.koog.prompt.executor.clients.LLMClientException
import com.fuelingchat.service.tool.GetFuelingInfoTool
import org.jetbrains.exposed.exceptions.ExposedSQLException

interface ChatService {
    suspend fun chat(
        prompt: String,
        fuelingId: String?,
        provider: String,
    ): ChatTurn
}

class ChatServiceImpl(
    private val history: DialogHistory,
    private val router: ProviderRouter,
    private val fuelingInfoTool: GetFuelingInfoTool,
    private val historyLimit: Int,
) : ChatService {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun chat(
        prompt: String,
        fuelingId: String?,
        provider: String,
    ): ChatTurn =
        try {
            val selection = router.resolve(provider)
            val summary =
                selection.client.run(
                    systemPrompt = systemPrompt(fuelingId),
                    history = history.recent(historyLimit),
                    userMessage = prompt,
                    tools = listOf(fuelingInfoTool),
                )
            history.appendTurn(prompt, summary)
            ChatTurn(summary, selection.name)
        } catch (error: Exception) {
            throw classifiedFailure(error)
        }

    private fun classifiedFailure(error: Exception): ChatFailure =
        when (error) {
            is ExposedSQLException -> {
                ChatFailure(ChatFailure.DB_FAILURE, DATABASES_UNAVAILABLE_MESSAGE)
            }

            is LLMClientException, is KoogHttpClientException, is AIAgentException -> {
                ChatFailure(ChatFailure.PROVIDER_FAILURE, PROVIDER_UNREACHABLE_MESSAGE)
            }

            else -> {
                throw error
            }
        }

    private fun systemPrompt(fuelingId: String?): String =
        if (fuelingId == null) {
            SYSTEM_PROMPT
        } else {
            SYSTEM_PROMPT + "\n" + attachedFuelingIdLine(fuelingId)
        }

    private fun attachedFuelingIdLine(fuelingId: String): String =
        "attached fueling_id: $fuelingId — use exactly this value for get_fueling_info"

    companion object {
        private const val SYSTEM_PROMPT =
            "Answer in the language of the user's latest message. " +
                "Answer fueling questions only via the get_fueling_info tool and never invent data."
        private const val DATABASES_UNAVAILABLE_MESSAGE = "Databases unavailable"
        private const val PROVIDER_UNREACHABLE_MESSAGE = "Provider unreachable"
    }
}
