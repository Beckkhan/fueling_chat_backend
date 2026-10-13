package com.fuelingchat.service

import ai.koog.agents.core.agent.exception.AIAgentException
import ai.koog.agents.core.tools.Tool
import ai.koog.http.client.KoogHttpClientException
import ai.koog.prompt.executor.clients.LLMClientException
import com.fuelingchat.service.tool.GetFuelingInfoTool
import org.jetbrains.exposed.exceptions.ExposedSQLException

/**
 * CONTRACT-SERVICE-001: orchestrates one chat turn on the pinned LlmClient seam (spec §3):
 * system prompt per D-6/D-9, dialog history snapshot (D-1 — read before this turn is
 * appended), never-empty tool set (D-8), provider routing (D-10); failures are classified
 * into the masked ChatFailure classes the T11 route maps to 500/502.
 */
class ChatServiceImpl(
    private val history: DialogHistory,
    private val historyLimit: Int, // DI: AppConfig.historyLimit — history passed = recent(historyLimit)
    private val router: ProviderRouter,
    private val getFuelingInfoTool: GetFuelingInfoTool,
) : ChatService {
    /**
     * One turn: resolve the provider, build the system prompt, snapshot the history, run the
     * client with the never-empty tool set, then append user + assistant lines and return
     * ChatTurn(summary, providerName). Only the four classified exception classes are caught;
     * anything else propagates (the route masks it as a generic 500).
     */
    override suspend fun run(request: ChatServiceRequest): ChatTurn {
        val selection = router.resolve(request.provider)
        val systemPrompt = buildSystemPrompt(request.fuelingId)
        val historyMessages = history.recent(historyLimit) // snapshot before this turn is appended
        val summary =
            try {
                selection.client.run(systemPrompt, historyMessages, request.prompt, toolSet())
            } catch (cause: ExposedSQLException) {
                throw classifyFailure(cause)
            } catch (cause: LLMClientException) {
                throw classifyFailure(cause)
            } catch (cause: KoogHttpClientException) {
                throw classifyFailure(cause)
            } catch (cause: AIAgentException) {
                throw classifyFailure(cause)
            }
        history.appendTurn(request.prompt, summary)
        return ChatTurn(summary, selection.name)
    }

    /**
     * D-6 + D-9: instruct to answer in the language of the user's latest message; fueling
     * questions answered only via get_fueling_info; when [fuelingId] is present the prompt
     * carries the exact line
     * `attached fueling_id: <id> — use exactly this value for get_fueling_info`
     * (the LLM never extracts the id from message text); when null, no such line.
     */
    private fun buildSystemPrompt(fuelingId: String?): String {
        val prompt =
            buildString {
                appendLine(
                    "You are a fueling data assistant for a mobile fueling service. " +
                        "Always answer in the language of the user's latest message.",
                )
                appendLine(
                    "Answer questions about fueling data only with the data returned by the " +
                        "get_fueling_info tool; never invent or guess fueling values.",
                )
                if (fuelingId != null) {
                    appendLine("attached fueling_id: $fuelingId — use exactly this value for get_fueling_info")
                }
            }
        return prompt.trimEnd()
    }

    /**
     * D-8: the tool set is assembled in the service layer and is NEVER empty
     * (spec §3 invariant, enforced by construction).
     */
    private fun toolSet(): List<Tool<*, *>> = listOf(getFuelingInfoTool)

    /**
     * Maps the four classified exception classes onto the masked failure classes:
     * ExposedSQLException -> DATABASE (500, "Databases unavailable");
     * LLMClientException / KoogHttpClientException / AIAgentException -> PROVIDER
     * (502, "Provider unreachable"). Messages come from the companion constants —
     * never SQL, credentials or raw provider text.
     */
    private fun classifyFailure(cause: Exception): ChatFailure =
        when (cause) {
            is ExposedSQLException -> {
                ChatFailure(ChatFailureStatus.DATABASE, MASKED_DATABASES_UNAVAILABLE)
            }

            is LLMClientException, is KoogHttpClientException, is AIAgentException -> {
                ChatFailure(ChatFailureStatus.PROVIDER, MASKED_PROVIDER_UNREACHABLE)
            }

            else -> {
                throw cause // no other class reaches here
            }
        }

    companion object {
        /** Masked 500 message (CONTRACT-API-002): any of the four DBs fails. */
        const val MASKED_DATABASES_UNAVAILABLE = "Databases unavailable"

        /** Masked 502 message (CONTRACT-API-002): provider unreachable / tool loop breaks. */
        const val MASKED_PROVIDER_UNREACHABLE = "Provider unreachable"
    }
}
