package com.example.point.infrastructure.adapters

import com.example.point.adapters.PointCache
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import kotlinx.datetime.*


@OptIn(ExperimentalLettuceCoroutinesApi::class)
class RedisPointCache(host: String, port: Int = 6379) : PointCache {
    private val redisClient: RedisClient
    private val connection: StatefulRedisConnection<String, String>
    private val commands: RedisCoroutinesCommands<String, String>

    init {
        redisClient =
            RedisClient.create(
                "redis://$host:$port",
            )
        connection = redisClient.connect()
        commands = connection.coroutines()
    }

    private fun getUserIdKey(userId: Long): String {
        return "${RedisKeys.USER_TOTAL_POINT_KEY_PREFIX}$userId"
    }

    private fun getUserValidExpireAtThresholdKey(userId: Long): String {
        return "${RedisKeys.USER_EXPIRY_THRESHOLD_PREFIX}$userId"
    }

    fun close() {
        redisClient.shutdown()
    }

    override suspend fun resetUserPoints(
        userId: Long,
        points: Int,
    ) {
        commands.set(getUserIdKey(userId), points.toString())
    }

    override suspend fun incrementUserPoints(
        userId: Long,
        points: Int,
    ) {
        commands.incrby(getUserIdKey(userId), points.toLong())
    }

    override suspend fun getUserPoint(userId: Long): Int {
        return commands.get(getUserIdKey(userId))?.toInt() ?: 0
    }

    override suspend fun getUserValidExpiryThreshold(userId: Long): LocalDateTime? {
        commands.get(getUserValidExpireAtThresholdKey(userId))?.toLong()?.let{
            return Instant.fromEpochSeconds(it).toLocalDateTime(TimeZone.UTC)
        }
        return null
    }

    override suspend fun setUserValidExpiryThreshold(userId: Long, expireAt: LocalDateTime) {
        commands.set(
            getUserValidExpireAtThresholdKey(userId),
            expireAt.toInstant(TimeZone.UTC).epochSeconds.toString()
        )
    }
}
