package com.example.point.domain.user.errors


class DuplicateCodeError(
    val userId: Long,
    val code: String,
) : RuntimeException(
    "User $userId already has point event with code : $code",
)
