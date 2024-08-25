package com.example.point.adapters

interface PointCache {

    suspend fun resetUserPoints(userId: Int, points: Int)
    suspend fun incrementUserPoints(userId: Int, points: Int)
}
