package com.fuelingchat.infra.db

import com.fuelingchat.config.AppConfig
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DbInfrastructureTest {
    @TempDir
    lateinit var tempDir: Path

    private val localFuelingUrl = "jdbc:postgresql://localhost:25432/fueling"
    private val localGasStationUrl = "jdbc:postgresql://localhost:25432/gas_station"
    private val localPaymentUrl = "jdbc:postgresql://localhost:25432/payment"
    private val localVendorsUrl = "jdbc:postgresql://localhost:25432/vendors"

    @Test
    fun allFourDatabasesExposeExpectedTables() {
        val pools = localPools()

        assertEquals(setOf("fuelings"), tableNames(pools.fueling))
        assertEquals(setOf("gas_stations"), tableNames(pools.gasStation))
        assertEquals(setOf("payments"), tableNames(pools.payment))
        assertEquals(setOf("fueling_events", "fueling_orders", "vendor_fueling_orders"), tableNames(pools.vendors))
    }

    @Test
    fun insertIsRejectedByReadOnlyServer() {
        val pools = localPools()

        val error =
            assertThrows(Exception::class.java) {
                transaction(pools.fueling) {
                    Fuelings.insert { it[fuelingId] = "F-99999" }
                }
            }

        assertTrue(error.message!!.contains("read-only"), "expected read-only rejection, got: ${error.message}")
    }

    @Test
    fun probePassesWhenAllDatabasesReachable() {
        val pools = localPools()

        DatabaseProbe(
            mapOf(
                "fueling" to pools.fueling,
                "gas_station" to pools.gasStation,
                "payment" to pools.payment,
                "vendors" to pools.vendors,
            ),
        ).checkAll()
    }

    @Test
    fun probeFailsNamingFirstUnreachableDatabase() {
        val pools =
            pools(
                fuelingUrl = "jdbc:postgresql://localhost:1/fueling",
                gasStationUrl = "jdbc:postgresql://localhost:1/gas_station",
                paymentUrl = "jdbc:postgresql://localhost:1/payment",
                vendorsUrl = "jdbc:postgresql://localhost:1/vendors",
            )

        val error =
            assertThrows(DatabaseUnreachableException::class.java) {
                DatabaseProbe(
                    mapOf(
                        "fueling" to pools.fueling,
                        "gas_station" to pools.gasStation,
                        "payment" to pools.payment,
                        "vendors" to pools.vendors,
                    ),
                ).checkAll()
            }

        assertEquals("database unreachable: fueling", error.message)
    }

    @Test
    fun probeFailsNamingUnreachableDatabaseAmongReachableOnes() {
        val pools =
            pools(
                fuelingUrl = localFuelingUrl,
                gasStationUrl = localGasStationUrl,
                paymentUrl = localPaymentUrl,
                vendorsUrl = "jdbc:postgresql://localhost:1/vendors",
            )

        val error =
            assertThrows(DatabaseUnreachableException::class.java) {
                DatabaseProbe(
                    mapOf(
                        "fueling" to pools.fueling,
                        "gas_station" to pools.gasStation,
                        "payment" to pools.payment,
                        "vendors" to pools.vendors,
                    ),
                ).checkAll()
            }

        assertEquals("database unreachable: vendors", error.message)
    }

    private fun tableNames(database: org.jetbrains.exposed.sql.Database): Set<String> =
        transaction(database) {
            exec("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'") { result ->
                buildSet {
                    while (result.next()) {
                        add(result.getString("table_name"))
                    }
                }
            }.orEmpty()
        }

    private fun localPools(): DbPools = pools(localFuelingUrl, localGasStationUrl, localPaymentUrl, localVendorsUrl)

    private fun pools(
        fuelingUrl: String,
        gasStationUrl: String,
        paymentUrl: String,
        vendorsUrl: String,
    ): DbPools =
        DbPools(
            AppConfig(
                envFile(
                    """
                    DB_USER=postgres
                    DB_PASSWORD=postgres
                    FUELING_DB_URL=$fuelingUrl
                    GAS_STATION_DB_URL=$gasStationUrl
                    PAYMENT_DB_URL=$paymentUrl
                    VENDORS_DB_URL=$vendorsUrl
                    DEEPSEEK_API_KEY=test-key
                    DEEPSEEK_MODEL=test-model
                    OLLAMA_MODEL=test-model
                    """.trimIndent(),
                ),
            ),
        )

    private fun envFile(content: String): Path {
        val file = tempDir.resolve("db-test.env")
        Files.writeString(file, content)
        return file
    }
}
