package com.example.point.infrastructure.adapters

import com.example.point.adapters.PointCache
import com.example.point.config.AppProperties
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.api.coroutines
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands

@OptIn(ExperimentalLettuceCoroutinesApi::class)
class RedisPointCache: PointCache {
    private val redisClient: RedisClient
    private val commands: RedisCoroutinesCommands<String, String>
    init {
        redisClient = RedisClient.create(
            "redis://${AppProperties.redisPassword}@${AppProperties.redisHost}:${AppProperties.redisPort}"
        )
        commands = redisClient.connect().coroutines()
    }

    private fun getUserIdKey(userId: Long): String {
        return "totalPoints:${userId}"
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

    fun close() {
        redisClient.shutdown()
    }
}