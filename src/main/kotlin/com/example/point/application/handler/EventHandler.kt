package com.example.point.application.handler

import com.example.point.adapters.PointCache
import com.example.point.domain.events.NotEnoughPointEvent

class EventHandler {
    suspend fun resetTotalPoint(event: NotEnoughPointEvent, pointCache: PointCache) {
        pointCache.resetUserPoints(
            userId = event.userId,
            points = event.totalPoints
        )
    }
}
