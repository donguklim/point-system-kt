package com.example.point.infrastructure.uow

import kotlin.random.Random
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.lettuce.core.pubsub.api.reactive.ChannelMessage
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock

import kotlin.properties.Delegates
import kotlin.time.DurationUnit


@OptIn(ExperimentalLettuceCoroutinesApi::class)
class RedisUserLockManager(host: String, port: Int = 6379, private val numBaseLocks: Int = 50){
    class Latch (
        userId: Long,
        pubSubConnection: StatefulRedisPubSubConnection<String, String>
    ){
        val mutex = Mutex()
        private val reactive = pubSubConnection.reactive()
        var messageFlow : Flow<ChannelMessage<String, String>>
        private val channelName = "user_lock_channel:${userId}"
        private var isAwaited = false

        init {
            reactive.subscribe(channelName).subscribe()
            messageFlow = reactive.observeChannels().asFlow()
        }

        suspend fun getMessage(): ChannelMessage<String, String> {
            return messageFlow.first()
        }

        suspend fun unsubscribe() {
            reactive.unsubscribe(channelName).awaitFirst()
        }
    }
    private val redisClient: RedisClient
    private val connection: StatefulRedisConnection<String, String>
    private val commands: RedisCoroutinesCommands<String, String>
    private val pubSubConnection: StatefulRedisPubSubConnection<String, String>
    private val pubsubCommands: RedisCoroutinesCommands<String, String>
    private val baseMutexes: List<Mutex> = List(numBaseLocks){Mutex()}
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

    private fun getKey(userId: Long): String {
        return "user_lock:${userId}"
    }

    suspend fun tryLock(
        userId: Long,
        lockId: Long,
        waitTimeMilliSeconds: Long = 500L,
        leaseTimeMilliSeconds: Long = 10000L
    ): Boolean {
        // Something to consider
        // 1. No timeout check is done for acquiring mutexes,
        // 2. No timeout check is done for redis commands
        val startAt = Clock.System.now()

        val key = getKey(userId)

        val luaScript = """
            if (redis.call('set', KEYS[1], NX, GET, EX, ARGV[2]) == ARGV[1]) then
                return nil;
            end;
            return redis.call('pttl', KEYS[1]);
            """.trimIndent()

        var lockTTl = commands.eval<Long?>(
            luaScript,
            io.lettuce.core.ScriptOutputType.INTEGER,
            arrayOf(key),
            "$lockId",
            "$leaseTimeMilliSeconds"
        )
        var ttl = waitTimeMilliSeconds - (Clock.System.now() - startAt).toLong(DurationUnit.MILLISECONDS)

        lockTTl?.let {
            if (it > ttl) return false
        } ?: return true

        var latch: Latch by Delegates.notNull()
        baseMutexes[(userId % numBaseLocks).toInt()].withLock {
            latch = userIdLatches.getOrPut(userId) { Latch(userId, pubSubConnection) }
            userIdCounts[userId] = userIdCounts.getOrPut(userId) { 0 } + 1
        }

        ttl = waitTimeMilliSeconds - (Clock.System.now() - startAt).toLong(DurationUnit.MILLISECONDS)

        try {
            while (ttl > 0){
                latch.mutex.withLock {
                    try {
                        withTimeout(ttl) {
                            latch.getMessage()
                        }
                        lockTTl = commands.eval<Long?>(
                            luaScript,
                            io.lettuce.core.ScriptOutputType.INTEGER,
                            arrayOf(key),
                            "$lockId",
                            "$leaseTimeMilliSeconds"
                        )
                    } catch (e: TimeoutCancellationException) {
                        return false
                    }
                }
                lockTTl?.let {
                    if (it > ttl) return false
                } ?: return true

                ttl = waitTimeMilliSeconds - (Clock.System.now() - startAt).toLong(DurationUnit.MILLISECONDS)
            }
        } finally {
            baseMutexes[(userId % numBaseLocks).toInt()].withLock {
                userIdCounts[userId] = userIdCounts[userId]!! - 1
                if (userIdCounts[userId] == 0){
                    userIdCounts.remove(userId)
                    userIdLatches.remove(userId)
                    latch.unsubscribe()
                }
            }
        }

        return false
    }

    suspend fun unlock(userId:Long, lockId:Long){
        val key = getKey(userId)

        val luaScript = """
            if (redis.call('get', KEYS[1]) == ARGV[1]) then
                redis.call('del', KEYS[1]);
                return 1;
            end;
            return 0;
            """.trimIndent()

        commands.eval<Long>(
            luaScript,
            io.lettuce.core.ScriptOutputType.INTEGER,
            arrayOf(key),
            "$lockId",
        )
    }
    suspend inline fun withLock(
        userId: Long,
        waitTimeMilliSeconds: Long = 500L,
        leaseTimeMilliSeconds: Long = 10000L,
        action: () -> Unit
    ): Boolean{
        val randomLockId = Random.nextLong()

        val isLocked = tryLock(userId, randomLockId, waitTimeMilliSeconds, leaseTimeMilliSeconds)

        if (!isLocked) {
            return false
        }

        action()
        unlock(userId, randomLockId)

        return true
    }
}
