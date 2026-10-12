package com.fuelingchat.infra.db

class DatabaseUnreachableException(
    databaseName: String,
    cause: Throwable,
) : RuntimeException("database unreachable: $databaseName", cause)
