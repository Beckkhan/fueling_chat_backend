package com.fuelingchat.infra.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object GasStations : Table("gas_stations") {
    val gasStationId = text("gas_station_id")
    val brand = text("brand")
    val address = text("address")
    val vendorGasStationId = text("vendor_gas_station_id")
    val latitude = double("latitude")
    val longitude = double("longitude")
    val activated = bool("activated")
    val deleted = bool("deleted")
    val fuelingLimits = text("fueling_limits").nullable()
    val gasStationName = text("gas_station_name")
    val manuallyDisabled = bool("manually_disabled")
    val updatedAt = timestampWithTimeZone("updated_at")
    val gasStationType = text("gas_station_type")
    val extra = text("extra")
    val locationId = text("location_id")
    val paymentMode = text("payment_mode")
    val subnet = text("subnet").nullable()
    val owner = text("owner").nullable()
    val publicBrand = text("public_brand").nullable()
    val takeOffMode = text("take_off_mode")
    val availableServices = text("available_services")

    override val primaryKey = PrimaryKey(gasStationId)
}
