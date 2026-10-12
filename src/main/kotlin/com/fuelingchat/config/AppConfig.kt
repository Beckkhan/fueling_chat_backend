package com.fuelingchat.config

import java.nio.file.Files
import java.nio.file.Path

class AppConfig(
    envFile: Path = Path.of(".env"),
) {
    private val values: Map<String, String> = parseEnvFile(envFile)

    val dbHost: String = valueOrDefault("DB_HOST", "localhost")
    val dbPort: Int = intValueOrDefault("DB_PORT", "25432")
    val dbUser: String = valueOrDefault("DB_USER", "postgres")
    val dbPassword: String = requiredValue("DB_PASSWORD")
    val fuelingDbUrl: String = valueOrDefault("FUELING_DB_URL", "jdbc:postgresql://localhost:25432/fueling")
    val gasStationDbUrl: String = valueOrDefault("GAS_STATION_DB_URL", "jdbc:postgresql://localhost:25432/gas_station")
    val paymentDbUrl: String = valueOrDefault("PAYMENT_DB_URL", "jdbc:postgresql://localhost:25432/payment")
    val vendorsDbUrl: String = valueOrDefault("VENDORS_DB_URL", "jdbc:postgresql://localhost:25432/vendors")
    val deepseekApiKey: String = requiredValue("DEEPSEEK_API_KEY")
    val deepseekModel: String = requiredValue("DEEPSEEK_MODEL")
    val ollamaBaseUrl: String = valueOrDefault("OLLAMA_BASE_URL", "http://localhost:11434/v1")
    val ollamaModel: String = requiredValue("OLLAMA_MODEL")
    val koogVersion: String = valueOrDefault("KOOG_VERSION", "1.3.0")
    val dialogHistoryFile: Path = Path.of(valueOrDefault("DIALOG_HISTORY_FILE", "data/dialog-history.jsonl"))
    val historyLimit: Int = intValueOrDefault("HISTORY_LIMIT", "20")

    private fun requiredValue(key: String): String =
        values[key]?.takeIf(String::isNotBlank) ?: throw ConfigException("missing required config key: $key")

    private fun valueOrDefault(
        key: String,
        default: String,
    ): String = values[key]?.takeIf(String::isNotBlank) ?: default

    private fun intValueOrDefault(
        key: String,
        default: String,
    ): Int = valueOrDefault(key, default).toIntOrNull() ?: throw ConfigException("invalid value for config key: $key")

    private fun parseEnvFile(file: Path): Map<String, String> {
        if (!Files.exists(file)) throw ConfigException(".env file not found: ${file.toAbsolutePath()}")
        return Files
            .readAllLines(file)
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) {
                    null
                } else {
                    line.substring(0, separator).trim() to line.substring(separator + 1).trim()
                }
            }.toMap()
    }
}
