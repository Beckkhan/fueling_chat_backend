package com.fuelingchat.service

import ai.koog.agents.core.agent.exception.AIAgentException
import ai.koog.agents.core.tools.Tool
import ai.koog.http.client.KoogHttpClientException
import ai.koog.prompt.executor.clients.LLMClientException
import com.fuelingchat.service.tool.GetFuelingInfoTool
import org.jetbrains.exposed.exceptions.ExposedSQLException

/**
 * Seam between the route (T11) and the service layer: one suspend entry point per chat turn
 * returning ChatTurn; failures are thrown as ChatFailure per the CONTRACT-API-001 error table.
 * DefaultChatService below is the T12 implementation; the DI binding lives in ServiceModule.
 */
interface ChatService {
    suspend fun chat(
        prompt: String,
        fuelingId: String?,
        provider: String?,
    ): ChatTurn
}

/**
 * CONTRACT-SERVICE-001: orchestrates one turn — system prompt (D-6/D-9), dialog history (D-1,
 * read before appending this turn), never-empty tool set (D-8), provider routing (D-10) — and
 * classifies failures into the masked 500/502 ChatFailure classes.
 */
class DefaultChatService(
    private val history: DialogHistory,
    private val historyLimit: Int,
    private val router: ProviderRouter,
    private val getFuelingInfoTool: GetFuelingInfoTool,
) : ChatService {
    override suspend fun chat(
        prompt: String,
        fuelingId: String?,
        provider: String?,
    ): ChatTurn {
        val selection = router.route(provider)
        val systemPrompt = buildSystemPrompt(fuelingId)
        val historyMessages = history.recent(historyLimit)
        val tools = toolSet()
        val summary =
            try {
                selection.client.run(systemPrompt, historyMessages, prompt, tools)
            } catch (cause: ExposedSQLException) {
                throw ChatFailure.databaseFailure(cause)
            } catch (cause: LLMClientException) {
                throw ChatFailure.providerFailure(cause)
            } catch (cause: KoogHttpClientException) {
                throw ChatFailure.providerFailure(cause)
            } catch (cause: AIAgentException) {
                throw ChatFailure.providerFailure(cause)
            }
        history.appendTurn(prompt, summary)
        return ChatTurn(summary, selection.name)
    }

    private fun buildSystemPrompt(fuelingId: String?): String =
        buildString {
            append("You are a fueling-data assistant. ")
            append("Always answer in the language of the user's latest message. ")
            append("Answer every question about fueling data only from the get_fueling_info tool result; ")
            append("never invent fueling details.")
            if (fuelingId != null) {
                append("\nattached fueling_id: $fuelingId — use exactly this value for get_fueling_info")
            }
        }

    private fun toolSet(): List<Tool<*, *>> = listOf(getFuelingInfoTool)
}
