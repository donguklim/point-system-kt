package com.example.point.infrastructure.adapters

import com.example.point.infrastructure.TestRedis
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisPointCacheTests {
    @BeforeAll
    fun setUp() {
        TestRedis
    }

    fun getConnection(): StatefulRedisConnection<String, String> {
        println("redis://:${TestRedis.password}@${TestRedis.redisContainer.host}:${TestRedis.redisContainer.getMappedPort(TestRedis.port)}")
        return RedisClient.create(
            "redis://:${TestRedis.password}@${TestRedis.redisContainer.host}:${TestRedis.redisContainer.getMappedPort(TestRedis.port)}",
        ).connect()
    }

    fun getCache(): RedisPointCache {
        return RedisPointCache(TestRedis.redisContainer.host, TestRedis.password, TestRedis.redisContainer.getMappedPort(TestRedis.port))
    }

    @Test
    fun testPointsReset() {
        val userId = (1..300L).random()
        val points = (1..3000).random()

        val cache = getCache()
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

        val cache = getCache()
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

        val cache = getCache()
        runBlocking {
            val cachedPoints = cache.getUserPoint(userId)
            assertEquals(points, cachedPoints)
        }
        cache.close()

        connection.flushCommands()
        connection.close()
    }
}
