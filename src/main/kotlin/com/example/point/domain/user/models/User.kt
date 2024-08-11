package com.example.point.domain.user.models

import kotlin.collections.Iterator

import com.example.point.domain.user.valueObjects.ChargedPoints
import com.example.point.domain.user.valueObjects.Consumption
import com.example.point.domain.user.errors.NotEnoughFetchedPointsError
import com.example.point.domain.user.events.*

class User(
    val userId: Int,
    private var pointsIter: Iterator<ChargedPoints>
) {
    private var consumptions: MutableList<Consumption> = mutableListOf()
    private var fetchedPoints: MutableList<ChargedPoints> = mutableListOf()
    private var events: MutableList<UserEvent> = mutableListOf()
    private var fetchedTotalPoints: Int = 0

    fun usePoints(consumption: Consumption): Boolean {
        val coast = consumption.getRemainingCoast()

        if (fetchedTotalPoints < coast){
            for (point in pointsIter) {
                fetchedPoints.add(point)
                fetchedTotalPoints += point.getLeftPoints()

                if (fetchedTotalPoints >= coast) break
            }
        }

        if (fetchedTotalPoints < coast){
            events.add(NotEnoughPointEvent(
                coast = coast,
                totalPoints = fetchedTotalPoints)
            )
            return false
        }

        while(consumption.getRemainingCoast() > 0 || fetchedPoints.size == 0){
            fetchedTotalPoints -= consumption.consume(fetchedPoints[0])
            if (fetchedPoints[0].getLeftPoints() <= 0) fetchedPoints.removeAt(0)

        }

        if (consumption.getRemainingCoast() >= 0) throw NotEnoughFetchedPointsError(
            userId = userId,
            totalFetchedPoints = fetchedTotalPoints,
            consumption_code = consumption.code,
            coast = coast
        )

        consumptions.add(consumption)

        return true
    }

}
