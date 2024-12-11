package com.example.point.domain.user.errors

class TransactionError(
    val userId: Long,
    val reason: String,
) : RuntimeException(
    "User $userId failed transaction. Reason: ${reason}",
)