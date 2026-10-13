package com.fuelingchat.service

import com.fuelingchat.infra.llm.LlmClient
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.instance

class ProviderRouter(
    private val di: DI,
) {
    fun resolve(provider: String?): ProviderSelection {
        val name = providerName(provider)
        return ProviderSelection(name, di.direct.instance<LlmClient>(tag = name))
    }

    private fun providerName(provider: String?): String =
        when (provider) {
            null, LOCAL_PROVIDER -> LOCAL_PROVIDER
            REMOTE_PROVIDER -> REMOTE_PROVIDER
            else -> throw ChatFailure(ChatFailure.BAD_PROVIDER, INVALID_PROVIDER_MESSAGE)
        }

    companion object {
        const val LOCAL_PROVIDER = "local"
        const val REMOTE_PROVIDER = "remote"
        private const val INVALID_PROVIDER_MESSAGE = "provider must be \"local\" or \"remote\""
    }
}

data class ProviderSelection(
    val name: String,
    val client: LlmClient,
)
