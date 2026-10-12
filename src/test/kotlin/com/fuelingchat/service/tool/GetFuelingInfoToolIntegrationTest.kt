package com.fuelingchat.service.tool

import com.fuelingchat.config.AppConfig
import com.fuelingchat.infra.db.DbPools
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path

class GetFuelingInfoToolIntegrationTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var tool: GetFuelingInfoTool
    private lateinit var pools: DbPools

    @BeforeEach
    fun createTool() {
        pools = localPools()
        tool = GetFuelingInfoTool(ToolDescriptorLoader().load(), pools)
    }

    @AfterEach
    fun closePools() {
        pools.close()
    }

    @Test
    fun resultHasExactlyTheSixContractKeys() {
        val result = execute("F-10001")

        assertEquals(
            setOf("fueling", "gas_station", "payments", "fueling_orders", "fueling_events", "vendor_fueling_orders"),
            result.keys,
        )
    }

    @Test
    fun fuelingSectionMatchesSeed() {
        val fueling = execute("F-10001")["fueling"]!!.jsonObject

        assertEquals("F-10001", fueling["fueling_id"]!!.jsonPrimitive.content)
        assertEquals("VFO-10001", fueling["vendor_fueling_order_id"]!!.jsonPrimitive.content)
        assertEquals("U-90001", fueling["user_id"]!!.jsonPrimitive.content)
        assertEquals("completed", fueling["status"]!!.jsonPrimitive.content)
        assertEquals(BigDecimal("1500.00"), fueling["amount"]!!.jsonPrimitive.content.toBigDecimal())
        assertEquals("AI-95", fueling["fuel_type"]!!.jsonPrimitive.content)
        assertEquals("GS-20001", fueling["gas_station_id"]!!.jsonPrimitive.content)
        assertEquals("PUMP-7", fueling["gas_pump_id"]!!.jsonPrimitive.content)
        assertEquals("GUN-3", fueling["refueling_gun_id"]!!.jsonPrimitive.content)
        assertEquals("RESV-10001", fueling["fuel_reservation_key"]!!.jsonPrimitive.content)
        assertEquals("2026-10-01T09:30:00Z", fueling["created_at"]!!.jsonPrimitive.content)
        assertEquals("2026-10-01T09:31:45Z", fueling["updated_at"]!!.jsonPrimitive.content)
        assertEquals(BigDecimal("1495.50"), fueling["actual_amount"]!!.jsonPrimitive.content.toBigDecimal())
        assertEquals("2026-10-01T09:30:00Z", fueling["vendor_transaction_date"]!!.jsonPrimitive.content)
        assertTrue(fueling["failed_reason"] is JsonNull)
        assertEquals(BigDecimal("52.49"), fueling["vendor_fuel_price"]!!.jsonPrimitive.content.toBigDecimal())
        assertEquals("""[{"orderId": "VFO-10001"}]""", fueling["fueled_orders"]!!.jsonPrimitive.content)
        assertTrue(fueling["discount_fuel_price"] is JsonNull)
        assertEquals("regular", fueling["fueling_type"]!!.jsonPrimitive.content)
        assertEquals("{}", fueling["extra"]!!.jsonPrimitive.content)
        assertEquals("prepayment", fueling["fueling_payment_type"]!!.jsonPrimitive.content)
        assertEquals("2026-10-01T09:30:05Z", fueling["finished_at"]!!.jsonPrimitive.content)
    }

    @Test
    fun gasStationSectionMatchesSeed() {
        val gasStation = execute("F-10001")["gas_station"]!!.jsonObject

        assertEquals("GS-20001", gasStation["gas_station_id"]!!.jsonPrimitive.content)
        assertEquals("FuelCorp", gasStation["brand"]!!.jsonPrimitive.content)
        assertEquals("Moscow, Tverskaya 7", gasStation["address"]!!.jsonPrimitive.content)
        assertEquals("VS-30001", gasStation["vendor_gas_station_id"]!!.jsonPrimitive.content)
        assertEquals(55.7601, gasStation["latitude"]!!.jsonPrimitive.content.toDouble())
        assertEquals(37.6184, gasStation["longitude"]!!.jsonPrimitive.content.toDouble())
        assertEquals(true, gasStation["activated"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(false, gasStation["deleted"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("""{"maxLiters": 100}""", gasStation["fueling_limits"]!!.jsonPrimitive.content)
        assertEquals("Tverskaya 7", gasStation["gas_station_name"]!!.jsonPrimitive.content)
        assertEquals(false, gasStation["manually_disabled"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("2026-09-15T08:00:00Z", gasStation["updated_at"]!!.jsonPrimitive.content)
        assertEquals("UNDEFINED", gasStation["gas_station_type"]!!.jsonPrimitive.content)
        assertEquals("{}", gasStation["extra"]!!.jsonPrimitive.content)
        assertEquals("msk_mo", gasStation["location_id"]!!.jsonPrimitive.content)
        assertEquals("PREPAID", gasStation["payment_mode"]!!.jsonPrimitive.content)
        assertTrue(gasStation["subnet"] is JsonNull)
        assertTrue(gasStation["owner"] is JsonNull)
        assertTrue(gasStation["public_brand"] is JsonNull)
        assertEquals("after", gasStation["take_off_mode"]!!.jsonPrimitive.content)
        assertEquals("{}", gasStation["available_services"]!!.jsonPrimitive.content)
    }

    @Test
    fun paymentsSectionMatchesSeed() {
        val payments = execute("F-10001")["payments"]!!.jsonArray

        assertEquals(2, payments.size)
        assertEquals(
            setOf("P-50001", "P-50002"),
            payments.map { it.jsonObject["payment_id"]!!.jsonPrimitive.content }.toSet(),
        )
        val succeeded = payments.single { it.jsonObject["payment_id"]!!.jsonPrimitive.content == "P-50001" }.jsonObject
        val failed = payments.single { it.jsonObject["payment_id"]!!.jsonPrimitive.content == "P-50002" }.jsonObject
        assertEquals("VFO-10001", succeeded["order_id"]!!.jsonPrimitive.content)
        assertEquals("EXT-777", succeeded["external_payment_id"]!!.jsonPrimitive.content)
        assertEquals("""{"state": "succeeded"}""", succeeded["status"]!!.jsonPrimitive.content)
        assertEquals("2026-10-01T09:31:45Z", succeeded["created_at"]!!.jsonPrimitive.content)
        assertEquals("2026-10-01T09:31:55Z", succeeded["updated_at"]!!.jsonPrimitive.content)
        assertEquals(BigDecimal("1500.00"), succeeded["amount"]!!.jsonPrimitive.content.toBigDecimal())
        assertEquals(BigDecimal("1495.50"), succeeded["actual_amount"]!!.jsonPrimitive.content.toBigDecimal())
        assertEquals("""{"state": "failed"}""", failed["status"]!!.jsonPrimitive.content)
        assertTrue(failed["actual_amount"] is JsonNull)
        assertTrue(failed["sbp_subscription_id"] is JsonNull)
    }

    @Test
    fun fuelingOrdersSectionMatchesSeed() {
        val fuelingOrders = execute("F-10001")["fueling_orders"]!!.jsonArray

        assertEquals(1, fuelingOrders.size)
        val fuelingOrder = fuelingOrders.first().jsonObject
        assertEquals("F-10001", fuelingOrder["fueling_id"]!!.jsonPrimitive.content)
        assertEquals(BigDecimal("28.50"), fuelingOrder["volume"]!!.jsonPrimitive.content.toBigDecimal())
        assertEquals(BigDecimal("52.49"), fuelingOrder["price"]!!.jsonPrimitive.content.toBigDecimal())
        assertEquals("VS-30001", fuelingOrder["station_id"]!!.jsonPrimitive.content)
        assertEquals("2026-10-01T09:30:00Z", fuelingOrder["created_at"]!!.jsonPrimitive.content)
        assertEquals("""{"checkId": "CHK-42"}""", fuelingOrder["data"]!!.jsonPrimitive.content)
        assertEquals("AI-95 premium", fuelingOrder["fuel_description"]!!.jsonPrimitive.content)
        assertEquals("FuelCorp", fuelingOrder["brand"]!!.jsonPrimitive.content)
    }

    @Test
    fun fuelingEventsSectionMatchesSeedOrderedByOffset() {
        val events = execute("F-10001")["fueling_events"]!!.jsonArray

        assertEquals(2, events.size)
        assertEquals(
            listOf(1L, 2L),
            events.map {
                it.jsonObject["event_offset"]!!
                    .jsonPrimitive.content
                    .toLong()
            },
        )
        val started = events.first().jsonObject
        assertEquals("started", started["status"]!!.jsonPrimitive.content)
        assertEquals("2026-10-01T09:29:58Z", started["created_at"]!!.jsonPrimitive.content)
        assertEquals("""{"pump": "PUMP-7"}""", started["data"]!!.jsonPrimitive.content)
        val completed = events.last().jsonObject
        assertEquals("completed", completed["status"]!!.jsonPrimitive.content)
        assertEquals("2026-10-01T09:30:05Z", completed["created_at"]!!.jsonPrimitive.content)
        assertEquals("""{"liters": 28.5}""", completed["data"]!!.jsonPrimitive.content)
    }

    @Test
    fun vendorFuelingOrdersSectionMatchesSeed() {
        val vendorOrders = execute("F-10001")["vendor_fueling_orders"]!!.jsonArray

        assertEquals(1, vendorOrders.size)
        val vendorOrder = vendorOrders.first().jsonObject
        assertEquals("F-10001", vendorOrder["fueling_id"]!!.jsonPrimitive.content)
        assertEquals("AI-95", vendorOrder["fuel_type"]!!.jsonPrimitive.content)
        assertEquals(BigDecimal("28.50"), vendorOrder["quantity"]!!.jsonPrimitive.content.toBigDecimal())
        assertEquals(BigDecimal("1495.50"), vendorOrder["total_sum"]!!.jsonPrimitive.content.toBigDecimal())
        assertEquals("PAID", vendorOrder["status"]!!.jsonPrimitive.content)
        assertEquals(BigDecimal("7"), vendorOrder["gas_pump_number"]!!.jsonPrimitive.content.toBigDecimal())
        assertEquals("driver@example.com", vendorOrder["email"]!!.jsonPrimitive.content)
        assertEquals("+7 900 000-00-01", vendorOrder["phone"]!!.jsonPrimitive.content)
        assertEquals("2026-10-01T09:31:45Z", vendorOrder["date_create"]!!.jsonPrimitive.content)
        assertEquals("2026-10-01T09:31:45Z", vendorOrder["created_at"]!!.jsonPrimitive.content)
    }

    @Test
    fun unknownFuelingIdReturnsEmptyShape() {
        val expected =
            Json.parseToJsonElement(
                """
                {
                  "fueling": null,
                  "gas_station": null,
                  "payments": [],
                  "fueling_orders": [],
                  "fueling_events": [],
                  "vendor_fueling_orders": []
                }
                """.trimIndent(),
            )

        assertEquals(expected, execute("F-UNKNOWN"))
    }

    private fun execute(fuelingId: String) =
        Json.parseToJsonElement(runBlocking { tool.execute(GetFuelingInfoArgs(fuelingId)) }).jsonObject

    private fun localPools(): DbPools =
        DbPools(
            AppConfig(
                envFile(
                    """
                    DB_USER=postgres
                    DB_PASSWORD=postgres
                    FUELING_DB_URL=jdbc:postgresql://localhost:25432/fueling
                    GAS_STATION_DB_URL=jdbc:postgresql://localhost:25432/gas_station
                    PAYMENT_DB_URL=jdbc:postgresql://localhost:25432/payment
                    VENDORS_DB_URL=jdbc:postgresql://localhost:25432/vendors
                    DEEPSEEK_API_KEY=test-key
                    DEEPSEEK_MODEL=test-model
                    OLLAMA_MODEL=test-model
                    """.trimIndent(),
                ),
            ),
        )

    private fun envFile(content: String): Path {
        val file = tempDir.resolve("tool-test.env")
        Files.writeString(file, content)
        return file
    }
}
