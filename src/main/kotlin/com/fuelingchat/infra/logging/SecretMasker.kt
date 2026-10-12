package com.fuelingchat.infra.logging

object SecretMasker {
    private val bearer = Regex("""(?i)(authorization\s*[:=]\s*bearer\s+)[^\s",;]+""")
    private val jsonField = Regex("""(?i)("(?:api_key|password|secret|token)"\s*:\s*)"[^"]*"""")
    private val keyValue =
        Regex("""(?i)\b(deepseek_api_key|db_password|api_key|password|secret|token)\s*=\s*[^\s,;&]+""")

    fun mask(text: String): String {
        val maskedBearer = bearer.replace(text) { "${it.groupValues[1]}***" }
        val maskedJson = jsonField.replace(maskedBearer) { "${it.groupValues[1]}\"***\"" }
        return keyValue.replace(maskedJson) { "${it.groupValues[1]}=***" }
    }
}
