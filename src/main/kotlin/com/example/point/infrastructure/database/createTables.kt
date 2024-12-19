package com.example.point.infrastructure.database

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction

fun main() {
    MYSQLDatabase.connect()
    transaction {
        addLogger(StdOutSqlLogger)
        SchemaUtils.create(PointEvents, PointDetails)
    }
}
