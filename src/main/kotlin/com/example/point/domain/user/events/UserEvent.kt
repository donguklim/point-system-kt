package com.example.point.domain.user.events

open class UserEvent {
}

class NotEnoughPointEvent(
    val coast: Int,
    val totalPoints: Int
): UserEvent()
