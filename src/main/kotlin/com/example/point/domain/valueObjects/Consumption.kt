package com.example.point.domain.valueObjects

import com.example.point.domain.valueObjects.ChargedPoints

class Consumption(
    val code: String,
    val cost: Int,
    val productCode: String,
    val description: String = "",
) {
    private var consumingPoints: Int = 0
    private var consumingChargedPoints: MutableList<ChargedPoints.PointUsage> = mutableListOf()

    init {
        if (cost <= 0) throw IllegalArgumentException("cost must be > 0 but was: $cost")
    }

    fun consume(points: ChargedPoints): Int{
        if (consumingPoints == cost) return 0

        val remaining = cost - consumingPoints
        val usage = points.consume(remaining)
        usage ?: return 0
        if (usage.points <= 0) return 0

        consumingPoints += usage.points
        consumingChargedPoints.add(usage)

        return usage.points
    }

    fun getRemainingCoast(): Int  = cost - consumingPoints

    fun collectUsedCharges(): List<ChargedPoints.PointUsage>{
        val ret = consumingChargedPoints
        consumingChargedPoints = mutableListOf()
        return ret

    }
}
