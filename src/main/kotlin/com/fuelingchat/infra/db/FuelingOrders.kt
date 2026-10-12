package com.fuelingchat.infra.db

import org.jetbrains.exposed.sql.Table

object FuelingOrders : Table("fueling_orders") {
    val fuelingId = text("fueling_id")
    val volume = decimal("volume", 10, 2)
    val price = decimal("price", 10, 2)
    val fuelType = text("fuel_type")
    val stationId = text("station_id")
    val pumpId = text("pump_id")
    val createdAt = long("created_at")
    val data = text("data")
    val fuelDescription = text("fuel_description").nullable()
    val brand = text("brand")

    override val primaryKey = PrimaryKey(fuelingId)
}
