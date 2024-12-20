package com.example.point

import com.example.point.infrastructure.database.MYSQLDatabase
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.runApplication


@SpringBootApplication(exclude = [DataSourceAutoConfiguration::class])
class PointApplication


fun main(args: Array<String>) {
    MYSQLDatabase.connect()
    runApplication<PointApplication>(*args)
}
