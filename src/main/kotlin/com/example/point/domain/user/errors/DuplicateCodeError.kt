package com.example.point.domain.user.errors

import com.example.point.infrastructure.database.PointType


class DuplicateCodeError(
    val userId: Long,
    val pointType: PointType,
    val code: String,
) : PointError(
    "Duplicate point code error - User $userId, point type ${pointType.value} : $code",
)
