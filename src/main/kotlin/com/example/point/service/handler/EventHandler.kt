package com.example.point.service.handler

import com.example.point.adapters.PointCache
import com.example.point.domain.events.NotEnoughPointEvent
import com.example.point.domain.events.PointChangeEvent

class EventHandler {
    suspend fun resetTotalPoints(event: NotEnoughPointEvent, pointCache: PointCache) {
        pointCache.resetUserPoints(
            userId = event.userId,
            points = event.totalPoints
        )
    }

    suspend fun addUserPoints(event: PointChangeEvent, pointCache: PointCache) {
        pointCache.incrementUserPoints(
            userId = event.userId,
            points = event.additionalPoints
        )
    }
}
