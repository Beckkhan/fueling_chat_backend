package com.fuelingchat

import com.fuelingchat.config.AppConfig
import com.fuelingchat.infra.db.DbPools
import com.fuelingchat.infra.llm.LlmClient
import com.fuelingchat.infra.logging.LoggerProvider
import com.fuelingchat.service.tool.GetFuelingInfoTool
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.kodein.di.direct
import org.kodein.di.instance
import java.nio.file.Files
import java.nio.file.Path

class DiSmokeTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun graphResolvesAllFoundationBindings() {
        val di = AppModule(envFile()).di

        assertNotNull(di.direct.instance<AppConfig>())
        assertNotNull(di.direct.instance<Database>(tag = "fueling"))
        assertNotNull(di.direct.instance<Database>(tag = "gas_station"))
        assertNotNull(di.direct.instance<Database>(tag = "payment"))
        assertNotNull(di.direct.instance<Database>(tag = "vendors"))
        assertNotNull(di.direct.instance<LoggerProvider>())
        assertNotNull(di.direct.instance<GetFuelingInfoTool>())
        assertNotNull(di.direct.instance<LlmClient>(tag = "local"))
        assertNotNull(di.direct.instance<LlmClient>(tag = "remote"))

        di.direct.instance<DbPools>().close()
    }

    private fun envFile(): Path {
        val file = tempDir.resolve("di-test.env")
        Files.writeString(
            file,
            """
            DB_PASSWORD=test-db
            DEEPSEEK_API_KEY=test-key
            DEEPSEEK_MODEL=test-model
            OLLAMA_MODEL=test-model
            """.trimIndent(),
        )
        return file
    }
}
