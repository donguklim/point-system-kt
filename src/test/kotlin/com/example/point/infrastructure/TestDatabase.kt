package com.example.point.infrastructure

import com.example.point.infrastructure.database.PointDetails
import com.example.point.infrastructure.database.PointEvents
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object TestDatabase {
    private val mysqlHost = System.getenv("MYSQL_URL")
    private val mysqlUser = System.getenv("MYSQL_USER")
    private val mysqlPassword = System.getenv("MYSQL_PASSWORD")

    init {

        val config =
            HikariConfig().apply {
                jdbcUrl = mysqlHost
                username = mysqlUser
                password = mysqlPassword
                driverClassName = "com.mysql.cj.jdbc.Driver"
                maximumPoolSize = 10
            }
        val dataSource = HikariDataSource(config)

        // This doesn't connect to the database but provides a descriptor for future usage
        // In the main app, we would do this on system start up
        // https://github.com/JetBrains/Exposed/wiki/Database-and-DataSource
        Database.connect(dataSource)

        // Create the schema
        transaction {
            SchemaUtils.create(PointEvents, PointDetails)
        }

    }
}
