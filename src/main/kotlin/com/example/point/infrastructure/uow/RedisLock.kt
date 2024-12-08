package com.example.point.infrastructure.uow

import com.example.point.adapters.PointCache
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import io.lettuce.core.pubsub.RedisPubSubListener
import io.lettuce.core.SetArgs
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.lettuce.core.pubsub.api.reactive.ChannelMessage
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import java.util.concurrent.CountDownLatch

import kotlin.properties.Delegates
import kotlin.time.DurationUnit


@OptIn(ExperimentalLettuceCoroutinesApi::class)
class RedisUserLockManager(host: String, port: Int = 6379){
    class Latch (
        userId: Long,
        pubSubConnection: StatefulRedisPubSubConnection<String, String>
    ){
        val semaphore = Semaphore(1)
        private val reactive = pubSubConnection.reactive()
        lateinit var messageFlow : Flow<ChannelMessage<String, String>>

        init {
            reactive.subscribe("user_lock_channel:${userId}").subscribe()
            val messageFlow = reactive.observeChannels().asFlow()
        }

        suspend fun getMessage(): ChannelMessage<String, String> {
            return messageFlow.first()
        }

        suspend fun acquire() {
            semaphore.acquire()
        }

        suspend fun release() {
            semaphore.release()
        }
    }
    private val redisClient: RedisClient
    private val connection: StatefulRedisConnection<String, String>
    private val commands: RedisCoroutinesCommands<String, String>
    private val pubSubConnection: StatefulRedisPubSubConnection<String, String>
    private val pubsubCommands: RedisCoroutinesCommands<String, String>
    private val baseSemaphore: Semaphore = Semaphore(1)
    private val userIdSemaphores: MutableMap<Long, Semaphore> = mutableMapOf()
    private val userIdCounts: MutableMap<Long, Int> = mutableMapOf()
    private val userIdLatches: MutableMap<Long, Latch> = mutableMapOf()

    init {
        redisClient =
            RedisClient.create(
                "redis://$host:$port",
            )
        connection = redisClient.connect()
        commands = connection.coroutines()

        pubSubConnection = redisClient.connectPubSub()
        pubsubCommands = pubSubConnection.coroutines()
    }

    fun close() {
        redisClient.shutdown()
    }

    suspend fun lock(userId: Long, waitTimeMilliSeconds: Long = 500L) {
        val startAt = Clock.System.now()

        val key = "user_lock:${userId}"
        var res = commands.set(key, "something", SetArgs.Builder.nx().px(waitTimeMilliSeconds))
        res?.let { return }

        var userSemaphore: Semaphore by Delegates.notNull()
        var latch: Latch by Delegates.notNull()

        baseSemaphore.withPermit {
            latch = userIdLatches.getOrPut(userId) { Latch(userId, pubSubConnection) }
            userIdCounts[userId] = userIdCounts.getOrPut(userId) { 0 } + 1
        }


        var ttl = (Clock.System.now() - startAt).toLong(DurationUnit.MILLISECONDS) - waitTimeMilliSeconds

        try {
            while (ttl > 0){
                latch.semaphore.withPermit {
                    try {
                        withTimeout(ttl) {
                            val message = latch.getMessage()
                            res = commands.set(key, "something", SetArgs.Builder.nx().px(waitTimeMilliSeconds))

                        }
                    } catch (e: TimeoutCancellationException) {
                        ttl = 0
                    }
                }
                if (res != null) break

                ttl = (Clock.System.now() - startAt).toLong(DurationUnit.MILLISECONDS) - waitTimeMilliSeconds
            }
        } finally {
            baseSemaphore.withPermit {
                userIdCounts[userId] = userIdCounts[userId]!! - 1
                if (userIdCounts[userId] == 0){
                    userIdCounts.remove(userId)
                    userIdLatches.remove(userId)
                }
            }
        }
        
        return
    }
}