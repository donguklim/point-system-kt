package com.example.point.infrastructure
import org.testcontainers.containers.GenericContainer


object TestRedis {
    val password = "232a"
    val port = 6379
    val redisContainer = GenericContainer<Nothing>("redis:latest").apply {
        withExposedPorts(port)
        withEnv("REDIS_PASSWORD", password)
        start()
    }

}