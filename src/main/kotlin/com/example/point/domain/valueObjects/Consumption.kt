package com.example.point.domain.valueObjects

class Consumption(
    val code: String,
    // unique code for each point event
    val cost: Int,
    val productCode: String,
    val title: String,
    val description: String = "",
) {
    private var consumingPoints: Int = 0
    private var consumingChargedPoints: MutableList<ChargedPoints.PointUsage> = mutableListOf()

    init {
        if (cost <= 0) throw IllegalArgumentException("cost must be > 0 but was: $cost")
    }

    fun consume(points: ChargedPoints): Int {
        if (consumingPoints == cost) return 0

        val usage = points.consume(getRemainingCoast())
        usage ?: return 0
        if (usage.points <= 0) return 0

        consumingPoints += usage.points
        consumingChargedPoints.add(usage)

        return usage.points
    }

    fun getRemainingCoast(): Int = cost - consumingPoints

    fun collectUsedCharges(): List<ChargedPoints.PointUsage> {
        val ret = consumingChargedPoints
        consumingChargedPoints = mutableListOf()
        return ret
    }
}
