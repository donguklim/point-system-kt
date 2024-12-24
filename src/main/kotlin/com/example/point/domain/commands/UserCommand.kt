package com.example.point.domain.commands

sealed class UserCommand(
    val userId: Long,
)

class PlayGameCommand(userId: Long, val betPoint: Int) : UserCommand(userId)

class GetDailyChargeCommand(userId: Long) : UserCommand(userId)

class PurchaseProductCommand(userId: Long, val productCode: String) : UserCommand(userId)
