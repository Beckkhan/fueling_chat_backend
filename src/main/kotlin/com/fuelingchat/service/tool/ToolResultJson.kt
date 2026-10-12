package com.fuelingchat.service.tool

import com.fuelingchat.infra.db.FuelingEvents
import com.fuelingchat.infra.db.FuelingOrders
import com.fuelingchat.infra.db.Fuelings
import com.fuelingchat.infra.db.GasStations
import com.fuelingchat.infra.db.Payments
import com.fuelingchat.infra.db.VendorFuelingOrders
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.sql.ResultRow
import java.math.BigDecimal
import java.time.Instant

object ToolResultJson {
    fun fueling(row: ResultRow): JsonObject =
        buildJsonObject {
            put("fueling_id", row[Fuelings.fuelingId])
            putNullable("vendor_fueling_order_id", row[Fuelings.vendorFuelingOrderId])
            put("user_id", row[Fuelings.userId])
            put("status", row[Fuelings.status])
            put("amount", row[Fuelings.amount])
            put("fuel_type", row[Fuelings.fuelType])
            put("gas_station_id", row[Fuelings.gasStationId])
            put("gas_pump_id", row[Fuelings.gasPumpId])
            put("refueling_gun_id", row[Fuelings.refuelingGunId])
            put("fuel_reservation_key", row[Fuelings.fuelReservationKey])
            put("created_at", row[Fuelings.createdAt].toEpochIso())
            put("updated_at", row[Fuelings.updatedAt].toEpochIso())
            putNullable("actual_amount", row[Fuelings.actualAmount]?.let { JsonPrimitive(it) })
            putNullable("vendor_transaction_date", row[Fuelings.vendorTransactionDate])
            putNullable("failed_reason", row[Fuelings.failedReason])
            putNullable("vendor_fuel_price", row[Fuelings.vendorFuelPrice]?.let { JsonPrimitive(it) })
            putNullable("fueled_orders", row[Fuelings.fueledOrders])
            putNullable("discount_fuel_price", row[Fuelings.discountFuelPrice]?.let { JsonPrimitive(it) })
            put("fueling_type", row[Fuelings.fuelingType])
            putNullable("extra", row[Fuelings.extra])
            put("fueling_payment_type", row[Fuelings.fuelingPaymentType])
            putNullable("finished_at", row[Fuelings.finishedAt]?.toEpochIso())
        }

    fun gasStation(row: ResultRow): JsonObject =
        buildJsonObject {
            put("gas_station_id", row[GasStations.gasStationId])
            put("brand", row[GasStations.brand])
            put("address", row[GasStations.address])
            put("vendor_gas_station_id", row[GasStations.vendorGasStationId])
            put("latitude", row[GasStations.latitude])
            put("longitude", row[GasStations.longitude])
            put("activated", row[GasStations.activated])
            put("deleted", row[GasStations.deleted])
            putNullable("fueling_limits", row[GasStations.fuelingLimits])
            put("gas_station_name", row[GasStations.gasStationName])
            put("manually_disabled", row[GasStations.manuallyDisabled])
            put("updated_at", row[GasStations.updatedAt].toInstant().toString())
            put("gas_station_type", row[GasStations.gasStationType])
            put("extra", row[GasStations.extra])
            put("location_id", row[GasStations.locationId])
            put("payment_mode", row[GasStations.paymentMode])
            putNullable("subnet", row[GasStations.subnet])
            putNullable("owner", row[GasStations.owner])
            putNullable("public_brand", row[GasStations.publicBrand])
            put("take_off_mode", row[GasStations.takeOffMode])
            put("available_services", row[GasStations.availableServices])
        }

    fun payment(row: ResultRow): JsonObject =
        buildJsonObject {
            put("payment_id", row[Payments.paymentId])
            put("order_id", row[Payments.orderId])
            put("user_id", row[Payments.userId])
            putNullable("external_payment_id", row[Payments.externalPaymentId])
            putNullable("status", row[Payments.status])
            putNullable("created_at", row[Payments.createdAt]?.toEpochIso())
            putNullable("updated_at", row[Payments.updatedAt]?.toEpochIso())
            put("payment_system", row[Payments.paymentSystem])
            put("payment_method", row[Payments.paymentMethod])
            put("payment_type", row[Payments.paymentType])
            put("purpose", row[Payments.purpose])
            putNullable("amount", row[Payments.amount]?.let { JsonPrimitive(it) })
            putNullable("actual_amount", row[Payments.actualAmount]?.let { JsonPrimitive(it) })
            putNullable("card_binding_id", row[Payments.cardBindingId])
            putNullable("card_binding_type", row[Payments.cardBindingType])
            putNullable("sbp_subscription_id", row[Payments.sbpSubscriptionId])
        }

    fun fuelingOrder(row: ResultRow): JsonObject =
        buildJsonObject {
            put("fueling_id", row[FuelingOrders.fuelingId])
            put("volume", row[FuelingOrders.volume])
            put("price", row[FuelingOrders.price])
            put("fuel_type", row[FuelingOrders.fuelType])
            put("station_id", row[FuelingOrders.stationId])
            put("pump_id", row[FuelingOrders.pumpId])
            put("created_at", row[FuelingOrders.createdAt].toEpochIso())
            put("data", row[FuelingOrders.data])
            putNullable("fuel_description", row[FuelingOrders.fuelDescription])
            put("brand", row[FuelingOrders.brand])
        }

    fun fuelingEvent(row: ResultRow): JsonObject =
        buildJsonObject {
            put("fueling_id", row[FuelingEvents.fuelingId])
            put("created_at", row[FuelingEvents.createdAt].toEpochIso())
            put("status", row[FuelingEvents.status])
            put("data", row[FuelingEvents.data])
            put("event_offset", row[FuelingEvents.eventOffset])
            put("brand", row[FuelingEvents.brand])
        }

    fun vendorFuelingOrder(row: ResultRow): JsonObject =
        buildJsonObject {
            put("fueling_id", row[VendorFuelingOrders.fuelingId])
            put("fuel_type", row[VendorFuelingOrders.fuelType])
            put("quantity", row[VendorFuelingOrders.quantity])
            put("total_sum", row[VendorFuelingOrders.totalSum])
            put("status", row[VendorFuelingOrders.status])
            put("gas_pump_number", row[VendorFuelingOrders.gasPumpNumber])
            put("vendor_gas_station_id", row[VendorFuelingOrders.vendorGasStationId])
            put("vendor_fuel_price", row[VendorFuelingOrders.vendorFuelPrice])
            putNullable("email", row[VendorFuelingOrders.email])
            putNullable("phone", row[VendorFuelingOrders.phone])
            put("date_create", row[VendorFuelingOrders.dateCreate])
            put("created_at", row[VendorFuelingOrders.createdAt].toEpochIso())
        }

    private fun JsonObjectBuilder.putNullable(
        key: String,
        value: String?,
    ) {
        if (value != null) {
            put(key, value)
        } else {
            put(key, JsonNull)
        }
    }

    private fun JsonObjectBuilder.putNullable(
        key: String,
        value: JsonElement?,
    ) {
        if (value != null) {
            put(key, value)
        } else {
            put(key, JsonNull)
        }
    }

    private fun BigDecimal.toEpochIso(): String = Instant.ofEpochMilli(toLong()).toString()

    private fun Long.toEpochIso(): String = Instant.ofEpochMilli(this).toString()
}
