package com.fuelingchat.service

import ai.koog.agents.core.agent.exception.AIAgentException
import ai.koog.agents.core.tools.Tool
import ai.koog.http.client.KoogHttpClientException
import ai.koog.prompt.executor.clients.LLMClientException
import com.fuelingchat.infra.llm.ChatMessage
import com.fuelingchat.infra.llm.LlmClient
import com.fuelingchat.infra.logging.LoggerProvider
import com.fuelingchat.service.tool.GetFuelingInfoTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.exceptions.ExposedSQLException

private const val ROLE_USER = "user"
private const val ROLE_ASSISTANT = "assistant"
private const val DATABASES_UNAVAILABLE = "Databases unavailable"
private const val PROVIDER_UNREACHABLE = "Provider unreachable"
private const val LANGUAGE_LINE = "Answer in the language of the user's latest message."
private const val TOOL_LINE = "Answer fueling questions only by calling the get_fueling_info tool."

/**
 * One chat turn (CONTRACT-SERVICE-001): routes the provider (D-10), assembles the system prompt (D-6/D-9),
 * the dialog history (D-1, D-11) and the never-empty tool set (D-8), runs the resolved [LlmClient] and
 * write-throughs the successful turn into [DialogHistory]. Failures are classified into masked [ChatFailure]s;
 * nothing is appended on failure.
 */
class ChatServiceImpl(
    private val dialogHistory: DialogHistory,
    private val providerRouter: ProviderRouter,
    private val getFuelingInfoTool: GetFuelingInfoTool,
    loggerProvider: LoggerProvider,
    private val localClient: LlmClient,
    private val remoteClient: LlmClient,
) : ChatService {
    /** Assembled here and never empty by construction (D-8); the tool call itself stays the model's choice. */
    private val tools: List<Tool<*, *>> = listOf(getFuelingInfoTool)

    private val logger = loggerProvider.logger(ChatServiceImpl::class.qualifiedName!!)

    override suspend fun runTurn(
        prompt: String,
        fuelingId: String?,
        provider: String?,
    ): ChatTurn {
        val resolved = providerRouter.resolve(provider)
        logger.info { "chat turn: provider=$resolved, prompt_length=${prompt.length}" }
        val summary = runResolved(resolved, fuelingId, prompt)
        withContext(Dispatchers.IO) {
            appendTurn(prompt, summary)
        }
        return ChatTurn(summary, resolved)
    }

    // Masking layer (CONTRACT-API-001): the raw cause — SQL, credentials or provider text — is dropped on
    // purpose, so the caught exception is never attached to the masked ChatFailure.
    @Suppress("SwallowedException")
    private suspend fun runResolved(
        provider: String,
        fuelingId: String?,
        userMessage: String,
    ): String =
        try {
            clientFor(provider).run(buildSystemPrompt(fuelingId), dialogHistory.recent(), userMessage, tools)
        } catch (failure: ExposedSQLException) {
            throw ChatFailure(500, DATABASES_UNAVAILABLE)
        } catch (failure: LLMClientException) {
            throw ChatFailure(502, PROVIDER_UNREACHABLE)
        } catch (failure: KoogHttpClientException) {
            throw ChatFailure(502, PROVIDER_UNREACHABLE)
        } catch (failure: AIAgentException) {
            throw ChatFailure(502, PROVIDER_UNREACHABLE)
        }

    private fun clientFor(provider: String): LlmClient =
        if (provider == ProviderRouter.REMOTE) remoteClient else localClient

    private fun buildSystemPrompt(fuelingId: String?): String =
        buildString {
            appendLine(LANGUAGE_LINE)
            appendLine(TOOL_LINE)
            fuelingId?.let { appendLine(attachedFuelingLine(it)) }
        }

    private fun attachedFuelingLine(fuelingId: String): String =
        "attached fueling_id: $fuelingId — use exactly this value for get_fueling_info"

    private suspend fun appendTurn(
        prompt: String,
        summary: String,
    ) {
        val timestamp = System.currentTimeMillis()
        dialogHistory.append(ChatMessage(ROLE_USER, prompt, timestamp))
        dialogHistory.append(ChatMessage(ROLE_ASSISTANT, summary, timestamp))
    }
}
