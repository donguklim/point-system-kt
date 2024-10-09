package com.example.point.infrastructure.adapters

import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RedisPointCacheTests {
    fun getConnection(): StatefulRedisConnection<String, String> {
        return RedisClient.create(
            "redis://4321@localhost:6379",
        ).connect()
    }

    @Test
    fun testPointsReset() {
        val userId = (1..300L).random()
        val points = (1..3000).random()

        val cache = RedisPointCache("localhost", "4321")
        runBlocking {
            cache.resetUserPoints(userId, points)
        }
        cache.close()

        val connection = getConnection()

        val value = connection.sync().get("totalPoints:$userId")?.toInt() ?: 0

        assertEquals(points, value)
        connection.flushCommands()
        connection.close()
    }

    @Test
    fun testPointsIncrement() {
        val userId = (1..300L).random()
        val points = (1..3000).random()

        val key = "totalPoints:$userId"
        val connection = getConnection()
        connection.sync().set(key, points.toString())

        val increasingValue = (1..3000).random()

        val cache = RedisPointCache("localhost", "4321")
        runBlocking {
            cache.incrementUserPoints(userId, increasingValue)
        }
        cache.close()

        val value = connection.sync().get(key)?.toInt() ?: 0

        assertEquals(points + increasingValue, value)
        connection.flushCommands()
        connection.close()
    }

    @Test
    fun testGetPoints() {
        val userId = (1..300L).random()
        val points = (1..3000).random()

        val key = "totalPoints:$userId"
        val connection = getConnection()
        connection.sync().set(key, points.toString())

        val cache = RedisPointCache("localhost", "4321")
        runBlocking {
            val cachedPoints = cache.getUserPoint(userId)
            assertEquals(points, cachedPoints)
        }
        cache.close()

        connection.flushCommands()
        connection.close()
    }
}
