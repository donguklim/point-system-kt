package com.example.point.domain.user.valueObjects

import com.example.point.domain.user.valueObjects.ChargedPoints

class Consumption(
    val id: String,
    val coast: Int,
    val productCode: String,
    val description: String,
) {
    private var consumingPoints: Int = 0
    private var consumingChargedPoints: MutableList<ChargedPoints.PointUsage> = mutableListOf()

    fun consume(points: ChargedPoints){
        if (consumingPoints == coast) return

        val remaining = coast - consumingPoints
        val usage = points.consume(remaining)
        usage ?: return
        if (usage.points <= 0) return

        consumingPoints += usage.points
        consumingChargedPoints.add(usage)
    }
}
