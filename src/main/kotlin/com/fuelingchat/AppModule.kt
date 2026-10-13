package com.fuelingchat

import com.fuelingchat.config.AppConfig
import com.fuelingchat.config.ConfigException
import com.fuelingchat.config.configModule
import com.fuelingchat.infra.db.DatabaseProbe
import com.fuelingchat.infra.db.DatabaseUnreachableException
import com.fuelingchat.infra.db.DbPools
import com.fuelingchat.infra.db.dbModule
import com.fuelingchat.infra.llm.clientModule
import com.fuelingchat.infra.logging.LoggerProvider
import com.fuelingchat.infra.logging.loggingModule
import com.fuelingchat.routes.chatRoutes
import com.fuelingchat.service.ChatService
import com.fuelingchat.service.serviceModule
import com.fuelingchat.service.tool.GetFuelingInfoTool
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.instance
import java.nio.file.Path

class AppModule(
    envFile: Path = Path.of(".env"),
) {
    val di =
        DI {
            import(configModule(envFile))
            import(dbModule)
            import(loggingModule)
            import(serviceModule)
            import(clientModule)
        }

    fun boot(): Int {
        val logger = di.direct.instance<LoggerProvider>().logger(AppModule::class.qualifiedName!!)
        val failure = configFailure() ?: databaseFailure() ?: toolSetFailure()
        if (failure != null) {
            logger.error { "boot failed: $failure" }
            return 1
        }
        runBlocking {
            embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
                // T11 wiring (integrator-owned): lazy resolution — the placeholder ChatService
                // binding must not fail boot; T12 keeps this wiring unchanged.
                chatRoutes { di.direct.instance<ChatService>() }
            }.start(wait = true)
        }
        return 0
    }

    private fun configFailure(): String? =
        try {
            di.direct.instance<AppConfig>()
            null
        } catch (error: ConfigException) {
            error.message
        }

    private fun databaseFailure(): String? =
        try {
            DatabaseProbe(databaseNames()).checkAll()
            null
        } catch (error: DatabaseUnreachableException) {
            error.message
        }

    private fun toolSetFailure(): String? =
        try {
            di.direct.instance<GetFuelingInfoTool>()
            null
        } catch (error: IllegalArgumentException) {
            error.message
        }

    private fun databaseNames(): Map<String, Database> {
        val pools = di.direct.instance<DbPools>()
        return mapOf(
            "fueling" to pools.fueling,
            "gas_station" to pools.gasStation,
            "payment" to pools.payment,
            "vendors" to pools.vendors,
        )
    }
}
