package com.fuelingchat.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

class AppConfigTest {
    @TempDir
    lateinit var tempDir: Path

    private val minimalEnv =
        """
        DB_PASSWORD=secret-db
        DEEPSEEK_API_KEY=secret-ds
        DEEPSEEK_MODEL=deepseek-chat
        OLLAMA_MODEL=qwen2.5
        """.trimIndent()

    @ParameterizedTest
    @ValueSource(strings = ["DB_PASSWORD", "DEEPSEEK_API_KEY", "DEEPSEEK_MODEL", "OLLAMA_MODEL"])
    fun missingRequiredKeyThrowsNamingTheKey(key: String) {
        val content = minimalEnv.lines().filterNot { it.startsWith("$key=") }.joinToString("\n")

        val error = assertThrows(ConfigException::class.java) { AppConfig(envFile(content)) }

        assertEquals("missing required config key: $key", error.message)
    }

    @ParameterizedTest
    @ValueSource(strings = ["DB_PASSWORD", "DEEPSEEK_API_KEY", "DEEPSEEK_MODEL", "OLLAMA_MODEL"])
    fun emptyRequiredKeyThrowsNamingTheKey(key: String) {
        val content = minimalEnv.replaceFirst("$key=.*".toRegex(), "$key=")

        val error = assertThrows(ConfigException::class.java) { AppConfig(envFile(content)) }

        assertEquals("missing required config key: $key", error.message)
    }

    @Test
    fun minimalEnvFileFallsBackToExampleDefaults() {
        val config = AppConfig(envFile(minimalEnv))

        assertEquals("localhost", config.dbHost)
        assertEquals(25432, config.dbPort)
        assertEquals("postgres", config.dbUser)
        assertEquals("secret-db", config.dbPassword)
        assertEquals("jdbc:postgresql://localhost:25432/fueling", config.fuelingDbUrl)
        assertEquals("jdbc:postgresql://localhost:25432/gas_station", config.gasStationDbUrl)
        assertEquals("jdbc:postgresql://localhost:25432/payment", config.paymentDbUrl)
        assertEquals("jdbc:postgresql://localhost:25432/vendors", config.vendorsDbUrl)
        assertEquals("secret-ds", config.deepseekApiKey)
        assertEquals("deepseek-chat", config.deepseekModel)
        assertEquals("http://localhost:11434", config.ollamaBaseUrl)
        assertEquals("qwen2.5", config.ollamaModel)
        assertEquals("1.3.0", config.koogVersion)
        assertEquals(Path.of("data/dialog-history.jsonl"), config.dialogHistoryFile)
        assertEquals(20, config.historyLimit)
    }

    @Test
    fun allKeysAreReadFromEnvFile() {
        val config =
            AppConfig(
                envFile(
                    """
                    DB_HOST=db.internal
                    DB_PORT=15432
                    DB_USER=app
                    DB_PASSWORD=pw
                    FUELING_DB_URL=jdbc:postgresql://db.internal:15432/fueling
                    GAS_STATION_DB_URL=jdbc:postgresql://db.internal:15432/gas_station
                    PAYMENT_DB_URL=jdbc:postgresql://db.internal:15432/payment
                    VENDORS_DB_URL=jdbc:postgresql://db.internal:15432/vendors
                    DEEPSEEK_API_KEY=ds-key
                    DEEPSEEK_MODEL=ds-model
                    OLLAMA_BASE_URL=http://ollama:11434/v1
                    OLLAMA_MODEL=llama3
                    KOOG_VERSION=9.9.9
                    DIALOG_HISTORY_FILE=journal.jsonl
                    HISTORY_LIMIT=42
                    """.trimIndent(),
                ),
            )

        assertEquals("db.internal", config.dbHost)
        assertEquals(15432, config.dbPort)
        assertEquals("app", config.dbUser)
        assertEquals("pw", config.dbPassword)
        assertEquals("jdbc:postgresql://db.internal:15432/fueling", config.fuelingDbUrl)
        assertEquals("jdbc:postgresql://db.internal:15432/gas_station", config.gasStationDbUrl)
        assertEquals("jdbc:postgresql://db.internal:15432/payment", config.paymentDbUrl)
        assertEquals("jdbc:postgresql://db.internal:15432/vendors", config.vendorsDbUrl)
        assertEquals("ds-key", config.deepseekApiKey)
        assertEquals("ds-model", config.deepseekModel)
        assertEquals("http://ollama:11434/v1", config.ollamaBaseUrl)
        assertEquals("llama3", config.ollamaModel)
        assertEquals("9.9.9", config.koogVersion)
        assertEquals(Path.of("journal.jsonl"), config.dialogHistoryFile)
        assertEquals(42, config.historyLimit)
    }

    @Test
    fun missingEnvFileThrowsNamingTheFile() {
        val error = assertThrows(ConfigException::class.java) { AppConfig(tempDir.resolve("absent.env")) }

        assertTrue(error.message!!.contains("absent.env"))
    }

    @Test
    fun commentsAndBlankLinesAreIgnored() {
        val config =
            AppConfig(
                envFile(
                    """
                    # local dev values
                    DB_PASSWORD=pw
                    DEEPSEEK_API_KEY=k1

                    DEEPSEEK_MODEL=m1
                    OLLAMA_MODEL=m2
                    """.trimIndent(),
                ),
            )

        assertEquals("pw", config.dbPassword)
        assertEquals("m2", config.ollamaModel)
    }

    @Test
    fun invalidHistoryLimitThrowsNamingTheKey() {
        val content = minimalEnv + "\nHISTORY_LIMIT=abc"

        val error = assertThrows(ConfigException::class.java) { AppConfig(envFile(content)) }

        assertEquals("invalid value for config key: HISTORY_LIMIT", error.message)
    }

    private fun envFile(content: String): Path {
        val file = tempDir.resolve("test.env")
        Files.writeString(file, content)
        return file
    }
}
