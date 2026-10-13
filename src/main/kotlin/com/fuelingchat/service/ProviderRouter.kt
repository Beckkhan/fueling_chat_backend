package com.fuelingchat.service

import com.fuelingchat.infra.llm.LlmClient

/**
 * The named LlmClient chosen for a turn. [name] is the provider name actually used and is
 * echoed in the CONTRACT-API-002 response; [client] is the binding that serves the turn.
 */
data class ProviderSelection(
    val name: String,
    val client: LlmClient,
)

/**
 * D-10: maps the request's provider field onto the named LlmClient bindings.
 * null or "local" -> local; "remote" -> remote; any other value -> masked ChatFailure.
 *
 * Over HTTP the route (T11) already rejects unknown providers at 400 before delegating
 * (CONTRACT-API-002), so this defensive branch is unreachable via POST /chat; the unit tests
 * pin it directly. The pinned T11 seam has no 400 member in ChatFailureStatus, so the failure
 * surfaces with the PROVIDER class (502) if it ever escapes.
 */
class ProviderRouter(
    private val local: LlmClient,
    private val remote: LlmClient,
) {
    /**
     * Resolves the named client for [provider] (null = default "local", D-10).
     *
     * @throws ChatFailure masked, for any value other than null/"local"/"remote" — the
     *   message never echoes the raw provider value.
     */
    fun resolve(provider: String?): ProviderSelection =
        when (provider) {
            null, LOCAL_PROVIDER -> ProviderSelection(LOCAL_PROVIDER, local)
            REMOTE_PROVIDER -> ProviderSelection(REMOTE_PROVIDER, remote)
            else -> throw ChatFailure(ChatFailureStatus.PROVIDER, MASKED_INVALID_PROVIDER)
        }

    companion object {
        const val LOCAL_PROVIDER = "local"
        const val REMOTE_PROVIDER = "remote"

        /** Masked failure message for an unknown provider — never echoes the raw value. */
        const val MASKED_INVALID_PROVIDER = "Invalid provider"
    }
}
