package com.fuelingchat.infra.db

import org.jetbrains.exposed.sql.Table

object FuelingEvents : Table("fueling_events") {
    val fuelingId = text("fueling_id")
    val createdAt = long("created_at")
    val status = text("status")
    val data = text("data")
    val eventOffset = long("event_offset")
    val brand = text("brand")

    override val primaryKey = PrimaryKey(eventOffset)
}
