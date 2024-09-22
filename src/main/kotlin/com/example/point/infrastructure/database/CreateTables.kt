package com.example.point.infrastructure.database

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction

fun main() {
    Database.connect("jdbc:mysql://localhost:5000/point_db", driver = "com.mysql.cj.jdbc.Driver", user = "pointer", password = "1234")

    transaction {
        addLogger(StdOutSqlLogger)

        SchemaUtils.create(PointEvents, PointDetails)
    }
}
