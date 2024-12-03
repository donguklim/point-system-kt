package com.example.point.infrastructure.adapters

import io.github.cdimascio.dotenv.dotenv
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals
import kotlin.test.assertNull
import java.net.InetAddress



fun getIpAddressByHostname(hostname: String): String {
    return try {
        val address = InetAddress.getByName(hostname)
        address.hostAddress
    } catch (e: Exception) {
        "Unable to resolve IP address for hostname: $hostname"
    }
}


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisPointCacheTests {
    private val envs = dotenv{
        filename = ".env.test"
    }

    // private val redisHost = System.getenv("CACHE_REDIS_HOST")

    // Somehow lettuce cannot connect to the Redis container with the container name as the host name
    // So get the ip address of the host name and use it instead.
    private val redisHost = getIpAddressByHostname(envs["CACHE_REDIS_HOST"])
    private val redisPort = envs["REDIS_PORT"].toInt()
    private val redisPassword = envs["REDIS_PASSWORD"]

    private fun getConnection(): StatefulRedisConnection<String, String> {
        return RedisClient.create(
            "redis://:$redisPassword@$redisHost:$redisPort",
        ).connect()
    }

    private fun getCache(): RedisPointCache {
        return RedisPointCache(redisHost, redisPassword, redisPort)
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

        val value = connection.sync().get("${RedisKeys.USER_TOTAL_POINT_KEY_PREFIX}$userId")?.toInt() ?: 0

        assertEquals(points, value)
        connection.flushCommands()
        connection.close()
    }

    @Test
    fun testPointsIncrement() {
        val userId = (1..300L).random()
        val points = (1..3000).random()

        val key = "${RedisKeys.USER_TOTAL_POINT_KEY_PREFIX}$userId"
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

        val key = "${RedisKeys.USER_TOTAL_POINT_KEY_PREFIX}$userId"
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

    @Test
    fun testGetExpireAt() {
        val userId = (1..300L).random()
        val secondsElapsed = (1..3000).random()

        val thresholdInstant = Clock.System.now().minus(
            secondsElapsed,
            DateTimeUnit.SECOND,
            TimeZone.UTC,
        )

        val key = "${RedisKeys.USER_EXPIRY_THRESHOLD_PREFIX}$userId"
        val connection = getConnection()
        connection.sync().set(key, thresholdInstant.epochSeconds.toString())

        val cache = getCache()
        runBlocking {
            val expiryThreshold = cache.getUserValidExpiryThreshold(userId)
            assertEquals(thresholdInstant.epochSeconds, expiryThreshold!!.toInstant(TimeZone.UTC).epochSeconds)
        }
        cache.close()

        connection.flushCommands()
        connection.close()
    }

    @Test
    fun testNullExpireAt() {
        val userId = (1..300L).random()

        val cache = getCache()
        runBlocking {
            val expiryThreshold = cache.getUserValidExpiryThreshold(userId)
            assertNull(expiryThreshold)
        }
        cache.close()

        val connection = getConnection()
        connection.flushCommands()
        connection.close()
    }

    @Test
    fun testSetExpireAt() {
        val userId = (1..300L).random()
        val secondsElapsed = (1..3000).random()

        val thresholdValue = Clock.System.now().minus(
            secondsElapsed,
            DateTimeUnit.SECOND,
            TimeZone.UTC,
        ).toLocalDateTime(TimeZone.UTC)

        val cache = getCache()
        runBlocking {
            cache.setUserValidExpiryThreshold(userId, thresholdValue)
        }
        cache.close()

        val key = "${RedisKeys.USER_EXPIRY_THRESHOLD_PREFIX}$userId"
        val connection = getConnection()
        val value = connection.sync().get(key)?.toLong()

        assertEquals(thresholdValue.toInstant(TimeZone.UTC).epochSeconds, value)

        connection.flushCommands()
        connection.close()
    }
}
