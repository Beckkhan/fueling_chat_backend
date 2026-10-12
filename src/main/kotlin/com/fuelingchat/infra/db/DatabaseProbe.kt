package com.fuelingchat.infra.db

import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.SQLException

class DatabaseProbe(
    private val databases: Map<String, Database>,
) {
    fun checkAll() {
        databases.forEach { (name, database) ->
            try {
                transaction(database) { exec("SELECT 1") }
            } catch (error: SQLException) {
                throw DatabaseUnreachableException(name, error)
            } catch (error: ExposedSQLException) {
                throw DatabaseUnreachableException(name, error)
            }
        }
    }
}
