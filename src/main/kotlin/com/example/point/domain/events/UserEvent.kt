package com.example.point.domain.events

open class UserEvent(
    val userId: Long
)

class NotEnoughPointEvent(
    userId: Long,
    val cost: Int,
    val totalPoints: Int,
) : UserEvent(userId)


class PointChangeEvent(
    userId: Long,
    val additionalPoints: Int,
): UserEvent(userId)
