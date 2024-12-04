package com.example.point.domain.events

open class UserEvent

class NotEnoughPointEvent(
    val userId: Long,
    val cost: Int,
    val totalPoints: Int,
) : UserEvent()
