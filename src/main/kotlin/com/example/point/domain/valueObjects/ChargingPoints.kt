package com.example.point.domain.valueObjects


class ChargingPoints (
    val code: String,
    val points: Int,
    val productCode: String,
    val description: String,
){
    init {
        if (points <= 0) throw IllegalArgumentException("points must be > 0 but was: $points")
    }
}
