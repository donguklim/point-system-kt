package com.example.point.domain.user.valueObjects

import com.example.point.domain.user.valueObjects.ChargedPoints

class Consumption(
    val code: String,
    val coast: Int,
    val productCode: String,
    val description: String,
) {
    private var consumingPoints: Int = 0
    private var consumingChargedPoints: MutableList<ChargedPoints.PointUsage> = mutableListOf()

    fun consume(points: ChargedPoints): Int{
        if (consumingPoints == coast) return 0

        val remaining = coast - consumingPoints
        val usage = points.consume(remaining)
        usage ?: return 0
        if (usage.points <= 0) return 0

        consumingPoints += usage.points
        consumingChargedPoints.add(usage)

        return usage.points
    }

    fun getRemainingCoast(): Int  = coast - consumingPoints
}
