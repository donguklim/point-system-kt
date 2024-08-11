package com.example.point.domain.user.valueObjects

import kotlin.math.min


class ChargedPoints (
    val chargeId: Int,
    private val initPoint: Int,
    private var usedPoints: Int = 0
){
    constructor(id: Int, points: Int) : this(id, points, 0)

    class PointUsage (val chargeId: Int, val points: Int)

    fun getLeftPoints(): Int{
        return initPoint - usedPoints
    }

    fun consume(points: Int): PointUsage {
        val consumingPoints = min(points, getLeftPoints())
        usedPoints += consumingPoints

        return PointUsage(chargeId, consumingPoints)
    }
}