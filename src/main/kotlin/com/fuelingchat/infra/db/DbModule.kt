package com.fuelingchat.infra.db

import org.jetbrains.exposed.sql.Database
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton

val dbModule =
    DI.Module("db") {
        bind<DbPools>() with singleton { DbPools(instance()) }
        bind<Database>(tag = "fueling") with singleton { instance<DbPools>().fueling }
        bind<Database>(tag = "gas_station") with singleton { instance<DbPools>().gasStation }
        bind<Database>(tag = "payment") with singleton { instance<DbPools>().payment }
        bind<Database>(tag = "vendors") with singleton { instance<DbPools>().vendors }
    }
