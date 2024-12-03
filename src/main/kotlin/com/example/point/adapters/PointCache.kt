package com.example.point.adapters

import com.example.point.domain.gamble.models.BettingGame
import kotlinx.datetime.LocalDateTime

interface PointCache {
    suspend fun resetUserPoints(
        userId: Long,
        points: Int,
    )

    suspend fun incrementUserPoints(
        userId: Long,
        points: Int,
    )

    suspend fun getUserPoint(userId: Long): Int

    suspend fun getUserValidExpiryThreshold(userId: Long): LocalDateTime?

    suspend fun setUserValidExpiryThreshold(userId: Long, expireAt: LocalDateTime)
}
