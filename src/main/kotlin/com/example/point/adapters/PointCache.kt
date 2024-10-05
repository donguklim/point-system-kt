package com.example.point.adapters

interface PointCache {
    suspend fun resetUserPoints(
        userId: Long,
        points: Int,
    )

    suspend fun incrementUserPoints(
        userId: Long,
        points: Int,
    )
}
