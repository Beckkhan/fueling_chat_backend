package com.fuelingchat.infra.db

import com.fuelingchat.config.AppConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database

class DbPools(
    config: AppConfig,
) {
    private val fuelingPool = BoundPool(config.fuelingDbUrl, config)
    private val gasStationPool = BoundPool(config.gasStationDbUrl, config)
    private val paymentPool = BoundPool(config.paymentDbUrl, config)
    private val vendorsPool = BoundPool(config.vendorsDbUrl, config)

    val fueling: Database = fuelingPool.database
    val gasStation: Database = gasStationPool.database
    val payment: Database = paymentPool.database
    val vendors: Database = vendorsPool.database

    fun close() {
        listOf(fuelingPool, gasStationPool, paymentPool, vendorsPool).forEach { it.close() }
    }

    private class BoundPool(
        url: String,
        config: AppConfig,
    ) {
        private val dataSource =
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = url
                    username = config.dbUser
                    password = config.dbPassword
                    maximumPoolSize = 4
                    connectionTimeout = 5_000
                    initializationFailTimeout = -1
                    isReadOnly = true
                    connectionInitSql = "SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY"
                },
            )

        val database: Database = Database.connect(dataSource)

        fun close() {
            dataSource.close()
        }
    }
}
