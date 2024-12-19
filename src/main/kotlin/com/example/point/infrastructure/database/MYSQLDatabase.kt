package com.example.point.infrastructure.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.cdimascio.dotenv.dotenv
import org.jetbrains.exposed.sql.Database

object MYSQLDatabase {
    private val envs = dotenv{
        filename = ".env"
    }
    private val mysqlHost =  envs["MYSQL_URL"]
    private val mysqlUser =  envs["MYSQL_USER"]
    private val mysqlPassword =  envs["MYSQL_PASSWORD"]



    fun connect() {
        println("mysql url $mysqlHost")
        println("mysql user $mysqlUser")
        val config =
            HikariConfig().apply {
                jdbcUrl = mysqlHost
                username = mysqlUser
                password = mysqlPassword
                driverClassName = "com.mysql.cj.jdbc.Driver"
            }
        val dataSource = HikariDataSource(config)

        // This doesn't connect to the database but provides a descriptor for future usage
        // In the main app, we would do this on system start up
        // https://github.com/JetBrains/Exposed/wiki/Database-and-DataSource
        Database.connect(dataSource)
    }
}
