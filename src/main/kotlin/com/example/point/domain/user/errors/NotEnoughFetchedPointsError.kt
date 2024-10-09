package com.example.point.domain.user.errors

class NotEnoughFetchedPointsError(
    val userId: Long,
    val totalFetchedPoints: Int,
    val consumption_code: String,
    val cost: Int,
) : RuntimeException(
        "User $userId with $totalFetchedPoints points " +
            "cannot consume item with code: $consumption_code, coast: $cost",
    )
