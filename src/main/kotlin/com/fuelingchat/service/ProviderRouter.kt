package com.fuelingchat.service

/**
 * Resolves the provider of one turn (D-10).
 *
 * An absent name defaults to [LOCAL] (mockup parity); a known name is echoed unchanged; any other value is
 * rejected with a masked `400` before any client is touched.
 */
class ProviderRouter {
    /**
     * @return [LOCAL] for `null` or `"local"`, [REMOTE] for `"remote"`.
     * @throws ChatFailure masked `400` when [requestedProvider] is neither known name.
     */
    fun resolve(requestedProvider: String?): String =
        when (requestedProvider) {
            null, LOCAL -> LOCAL
            REMOTE -> REMOTE
            else -> throw ChatFailure(400, "Unknown provider")
        }

    companion object {
        /** Named `LlmClient` binding tag served by the local client (T13). */
        const val LOCAL = "local"

        /** Named `LlmClient` binding tag served by the remote client (T14). */
        const val REMOTE = "remote"
    }
}
