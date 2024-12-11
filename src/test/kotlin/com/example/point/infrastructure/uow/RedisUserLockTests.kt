package com.example.point.infrastructure.uow

import kotlinx.coroutines.*
import com.example.point.infrastructure.adapters.getIpAddressByHostname
import io.github.cdimascio.dotenv.dotenv
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisUserLockTests {
    private val envs = dotenv{
        filename = ".env.test"
    }

    // private val redisHost = System.getenv("CACHE_REDIS_HOST")

    // Somehow lettuce cannot connect to the Redis container with the container name as the host name
    // So get the ip address of the host name and use it instead.
    private val redisHost = getIpAddressByHostname(envs["CACHE_REDIS_HOST"])
    private val redisPort = envs["REDIS_PORT"].toInt()

    private fun getConnection(): StatefulRedisConnection<String, String> {
        return RedisClient.create(
            "redis://$redisHost:$redisPort",
        ).connect()
    }

    private fun getManager(): RedisUserLockManager {
        return RedisUserLockManager(redisHost, redisPort)
    }

    @Test
    fun testLocking() {
        val userId = (1..300L).random()

        val manager = getManager()

        val lockingId = 1232L
        var isLocked = false
        var isAnotherLocked = false
        val connection = getConnection()

        assertNull(connection.sync().get("user_lock:${userId}"))

        runBlocking {
            isLocked = manager.tryLock(userId, lockingId, 100, 10021)
            isAnotherLocked = manager.tryLock(userId, lockingId + 1, 100, 100000)
        }
        assertTrue(isLocked)
        assertFalse(isAnotherLocked)

        assertEquals(lockingId, manager.connection.sync().get("user_lock:${userId}")?.toLong() ?: -1L)

        runBlocking {
            manager.unlock(userId, lockingId)
        }
        assertNull(connection.sync().get("user_lock:${userId}"))

        connection.flushCommands()
        connection.close()
    }

    @Test
    fun testSimultaneousAccesses() {
        val userId = (1..300L).random()

        val manager = getManager()

        var counter = 0
        runBlocking {
            val jobs = List(100) { index ->
                launch(Dispatchers.IO) {
                    manager.tryLock(userId, index.toLong(), 10000, 100000)
                    counter = counter + 1
                }
            }
            jobs.forEach { it.join() }
        }

        val stopBefore = (10..77).random()
        runBlocking {
            val jobs = List(stopBefore) { index ->
                launch(Dispatchers.IO) {
                    manager.unlock(userId, index.toLong())
                }
            }
            jobs.forEach { it.join() }
        }

        assertEquals(stopBefore, counter)

        runBlocking {
            val jobs = List(100 - stopBefore) { index ->
                launch(Dispatchers.IO) {
                    manager.unlock(userId, (index + stopBefore).toLong())
                }
            }
            jobs.forEach { it.join() }
        }

        val connection = getConnection()
        connection.flushCommands()
        connection.close()
    }
}