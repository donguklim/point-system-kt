package com.example.point

import com.example.point.infrastructure.database.MYSQLDatabase
import kotlinx.coroutines.delay
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.lang.Thread.sleep

@SpringBootApplication
class PointApplication

fun main(args: Array<String>) {
    MYSQLDatabase.connect()
    runApplication<PointApplication>(*args)
}
