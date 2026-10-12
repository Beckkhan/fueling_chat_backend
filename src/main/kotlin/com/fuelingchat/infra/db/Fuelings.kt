package com.fuelingchat.infra.db

import org.jetbrains.exposed.sql.Table

object Fuelings : Table("fuelings") {
    val fuelingId = text("fueling_id")
    val vendorFuelingOrderId = text("vendor_fueling_order_id").nullable()
    val userId = text("user_id")
    val status = text("status")
    val amount = decimal("amount", 10, 2)
    val fuelType = text("fuel_type")
    val gasStationId = text("gas_station_id")
    val gasPumpId = text("gas_pump_id")
    val refuelingGunId = text("refueling_gun_id")
    val fuelReservationKey = text("fuel_reservation_key")
    val createdAt = decimal("created_at", 20, 0)
    val updatedAt = decimal("updated_at", 20, 0)
    val actualAmount = decimal("actual_amount", 10, 2).nullable()
    val vendorTransactionDate = text("vendor_transaction_date").nullable()
    val failedReason = text("failed_reason").nullable()
    val vendorFuelPrice = decimal("vendor_fuel_price", 10, 2).nullable()
    val fueledOrders = text("fueled_orders").nullable()
    val discountFuelPrice = decimal("discount_fuel_price", 10, 2).nullable()
    val fuelingType = text("fueling_type")
    val extra = text("extra").nullable()
    val fuelingPaymentType = text("fueling_payment_type")
    val finishedAt = decimal("finished_at", 20, 0).nullable()

    override val primaryKey = PrimaryKey(fuelingId)
}
