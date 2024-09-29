package com.example.point.domain.valueObjects

import kotlin.math.min

class ChargedPoints(
    val chargeId: Long,
    private val initPoint: Int,
) {
    private var usedPoints: Int = 0

    class PointUsage(val chargeId: Long, val points: Int)

    fun getLeftPoints(): Int {
        return initPoint - usedPoints
    }

    fun consume(points: Int): PointUsage? {
        val consumingPoints = min(points, getLeftPoints())
        if (consumingPoints <= 0) return null

        usedPoints += consumingPoints

        return PointUsage(chargeId, consumingPoints)
    }
}
