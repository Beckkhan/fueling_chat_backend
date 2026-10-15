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
import com.fuelingchat.routes.installCors
import com.fuelingchat.service.ChatService
import com.fuelingchat.service.serviceModule
import com.fuelingchat.service.tool.GetFuelingInfoTool
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.coroutines.awaitCancellation
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
                install(ContentNegotiation) {
                    json()
                }
                installCors()
                routing {
                    chatRoutes { di.direct.instance<ChatService>() }
                }
            }.start(wait = false)
            // Keep the runBlocking event loop alive without blocking it: request coroutines inherit this
            // event loop as their interceptor, and a blocked loop (start(wait = true) waits on a Netty
            // close-future) would never process their resumptions, stalling every suspending handler.
            awaitCancellation()
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
