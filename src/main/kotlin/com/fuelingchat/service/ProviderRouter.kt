package com.fuelingchat.service

import com.fuelingchat.infra.llm.LlmClient

/**
 * The named LlmClient chosen for a turn. `name` is the provider name actually used and is
 * echoed in the CONTRACT-API-002 response; `client` is the binding that serves the turn.
 */
data class ProviderSelection(
    val name: String,
    val client: LlmClient,
)

/**
 * D-10: maps the request's optional provider field onto the named LlmClient bindings
 * (`local` / `remote`). Absent -> "local" (default); any other value -> masked 400 ChatFailure.
 */
class ProviderRouter(
    private val local: LlmClient,
    private val remote: LlmClient,
) {
    fun route(provider: String?): ProviderSelection =
        when (provider) {
            null, "local" -> ProviderSelection("local", local)
            "remote" -> ProviderSelection("remote", remote)
            else -> throw ChatFailure(400, "Invalid provider")
        }
}
