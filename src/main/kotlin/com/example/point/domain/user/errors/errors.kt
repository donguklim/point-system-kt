package com.example.point.domain.user.errors


class NotEnoughFetchedPointsError(
    val userId: Int,
    val totalFetchedPoints: Int,
    val consumption_code: String,
    val coast: Int,
): RuntimeException(
    "User $userId with $totalFetchedPoints points " +
        "cannot consume item with code: $consumption_code, coast: $coast"
)
