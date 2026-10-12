package com.fuelingchat.infra.db

import org.jetbrains.exposed.sql.Table

object VendorFuelingOrders : Table("vendor_fueling_orders") {
    val fuelingId = text("fueling_id")
    val fuelType = text("fuel_type")
    val quantity = decimal("quantity", 10, 2)
    val totalSum = decimal("total_sum", 10, 2)
    val status = text("status")
    val gasPumpNumber = decimal("gas_pump_number", 10, 0)
    val vendorGasStationId = text("vendor_gas_station_id")
    val vendorFuelPrice = decimal("vendor_fuel_price", 10, 2)
    val email = text("email").nullable()
    val phone = text("phone").nullable()
    val dateCreate = text("date_create")
    val createdAt = decimal("created_at", 20, 0)

    override val primaryKey = PrimaryKey(fuelingId)
}
