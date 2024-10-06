package com.example.point.infrastructure.database

import com.example.point.config.AppProperties
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction

fun main() {
    Database.connect(
        AppProperties.datasourceUrl,
        driver = "com.mysql.cj.jdbc.Driver",
        user = AppProperties.datasourceUsername,
        password = AppProperties.datasourcePassword)

    transaction {
        addLogger(StdOutSqlLogger)

        SchemaUtils.create(PointEvents, PointDetails)
    }
}
