package com.fuelingchat.service.tool

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.serialization.typeToken
import com.fuelingchat.infra.db.DbPools
import com.fuelingchat.infra.db.FuelingEvents
import com.fuelingchat.infra.db.FuelingOrders
import com.fuelingchat.infra.db.Fuelings
import com.fuelingchat.infra.db.GasStations
import com.fuelingchat.infra.db.Payments
import com.fuelingchat.infra.db.VendorFuelingOrders
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class GetFuelingInfoTool(
    descriptor: ToolDescriptorData,
    private val pools: DbPools,
) : SimpleTool<GetFuelingInfoArgs>(
        argsType = typeToken<GetFuelingInfoArgs>(),
        name = descriptor.name,
        description = descriptor.description,
    ) {
    override suspend fun execute(args: GetFuelingInfoArgs): String {
        val fueling = selectFueling(args.fueling_id)
        val gasStation = fueling?.let { selectGasStation(it[Fuelings.gasStationId]) }
        val payments = fueling?.let { it[Fuelings.vendorFuelingOrderId]?.let(::selectPayments) }.orEmpty()
        val fuelingOrders = selectFuelingOrders(args.fueling_id)
        val fuelingEvents = selectFuelingEvents(args.fueling_id)
        val vendorFuelingOrders = selectVendorFuelingOrders(args.fueling_id)

        return buildJsonObject {
            put("fueling", fueling?.let(ToolResultJson::fueling) ?: JsonNull)
            put("gas_station", gasStation?.let(ToolResultJson::gasStation) ?: JsonNull)
            put("payments", JsonArray(payments.map(ToolResultJson::payment)))
            put("fueling_orders", JsonArray(fuelingOrders.map(ToolResultJson::fuelingOrder)))
            put("fueling_events", JsonArray(fuelingEvents.map(ToolResultJson::fuelingEvent)))
            put("vendor_fueling_orders", JsonArray(vendorFuelingOrders.map(ToolResultJson::vendorFuelingOrder)))
        }.toString()
    }

    private fun selectFueling(fuelingId: String): ResultRow? =
        transaction(pools.fueling) {
            Fuelings
                .selectAll()
                .where { Fuelings.fuelingId eq fuelingId }
                .orderBy(Fuelings.createdAt to SortOrder.DESC)
                .limit(1)
                .firstOrNull()
        }

    private fun selectGasStation(gasStationId: String): ResultRow? =
        transaction(pools.gasStation) {
            GasStations
                .selectAll()
                .where { GasStations.gasStationId eq gasStationId }
                .limit(1)
                .firstOrNull()
        }

    private fun selectPayments(orderId: String): List<ResultRow> =
        transaction(pools.payment) {
            Payments
                .selectAll()
                .where { Payments.orderId eq orderId }
                .orderBy(Payments.createdAt to SortOrder.DESC)
                .limit(100)
                .toList()
        }

    private fun selectFuelingOrders(fuelingId: String): List<ResultRow> =
        transaction(pools.vendors) {
            FuelingOrders
                .selectAll()
                .where { FuelingOrders.fuelingId eq fuelingId }
                .orderBy(FuelingOrders.createdAt to SortOrder.DESC)
                .limit(100)
                .toList()
        }

    private fun selectFuelingEvents(fuelingId: String): List<ResultRow> =
        transaction(pools.vendors) {
            FuelingEvents
                .selectAll()
                .where { FuelingEvents.fuelingId eq fuelingId }
                .orderBy(FuelingEvents.eventOffset to SortOrder.ASC)
                .limit(100)
                .toList()
        }

    private fun selectVendorFuelingOrders(fuelingId: String): List<ResultRow> =
        transaction(pools.vendors) {
            VendorFuelingOrders
                .selectAll()
                .where { VendorFuelingOrders.fuelingId eq fuelingId }
                .orderBy(VendorFuelingOrders.createdAt to SortOrder.DESC)
                .limit(100)
                .toList()
        }
}
