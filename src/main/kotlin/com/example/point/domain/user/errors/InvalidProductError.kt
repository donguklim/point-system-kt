package com.example.point.domain.user.errors

import com.example.point.infrastructure.database.PointType


class InvalidProductError(
    val userId: Long,
    val productCode: String,
) : PointError(
    "Invalid product purchase from User $userId. Product with code ${productCode} does not exist.",
)
