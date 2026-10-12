package com.fuelingchat.infra.db

import com.fuelingchat.config.AppConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database

class DbPools(
    config: AppConfig,
) {
    val fueling: Database = connect(config.fuelingDbUrl, config)
    val gasStation: Database = connect(config.gasStationDbUrl, config)
    val payment: Database = connect(config.paymentDbUrl, config)
    val vendors: Database = connect(config.vendorsDbUrl, config)

    private fun connect(
        url: String,
        config: AppConfig,
    ): Database {
        val dataSource =
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
        return Database.connect(dataSource)
    }
}
