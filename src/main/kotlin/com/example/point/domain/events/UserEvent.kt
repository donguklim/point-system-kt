package com.example.point.domain.events

open class UserEvent {
}

class NotEnoughPointEvent(
    val cost: Int,
    val totalPoints: Int
): UserEvent()
