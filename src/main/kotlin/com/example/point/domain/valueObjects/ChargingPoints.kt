package com.example.point.domain.valueObjects

class ChargingPoints(
    // unique code for each point event
    val code: String,
    val numPoints: Int,
    val title: String,
    val description: String,
) {
    init {
        if (numPoints <= 0) throw IllegalArgumentException("points must be > 0 but was: $numPoints")
    }
}
