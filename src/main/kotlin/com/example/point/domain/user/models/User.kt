package com.example.point.domain.user.models

import com.example.point.domain.events.*
import com.example.point.domain.events.NotEnoughPointEvent
import com.example.point.domain.events.UserEvent
import com.example.point.domain.user.errors.NotEnoughFetchedPointsError
import com.example.point.domain.valueObjects.ChargedPoints
import com.example.point.domain.valueObjects.ChargingPoints
import com.example.point.domain.valueObjects.Consumption

class User(
    val userId: Int,
    private var pointsIter: Sequence<ChargedPoints>,
) {
    private var consumptions: MutableList<Consumption> = mutableListOf()
    private var chargingPoints: MutableList<ChargingPoints> = mutableListOf()
    private var fetchedPoints: MutableList<ChargedPoints> = mutableListOf()
    private var events: MutableList<UserEvent> = mutableListOf()
    private var fetchedTotalPoints: Int = 0

    fun usePoints(consumption: Consumption): Boolean {
        val cost = consumption.getRemainingCoast()

        if (fetchedTotalPoints < cost)
            {
                for (point in pointsIter) {
                    fetchedPoints.add(point)
                    fetchedTotalPoints += point.getLeftPoints()

                    if (fetchedTotalPoints >= cost) break
                }
            }

        if (fetchedTotalPoints < cost)
            {
                events.add(
                    NotEnoughPointEvent(
                        cost = cost,
                        totalPoints = fetchedTotalPoints,
                    ),
                )
                return false
            }

        while (consumption.getRemainingCoast() > 0 && fetchedPoints.size > 0) {
            fetchedTotalPoints -= consumption.consume(fetchedPoints[0])
            if (fetchedPoints[0].getLeftPoints() <= 0) {
                fetchedPoints.removeAt(0)
            }
        }

        if (consumption.getRemainingCoast() > 0) {
            throw NotEnoughFetchedPointsError(
                userId = userId,
                totalFetchedPoints = fetchedTotalPoints,
                consumption_code = consumption.code,
                cost = cost,
            )
        }

        consumptions.add(consumption)

        return true
    }

    fun chargePoints(points: ChargingPoints)  {
        chargingPoints.add(points)
    }

    fun collectEvents() =
        sequence<UserEvent> {
            while (events.isNotEmpty()) {
                val event = events[0]
                events.removeAt(0)
                yield(event)
            }
        }

    fun collectConsumptions() =
        sequence<Consumption> {
            while (consumptions.isNotEmpty()) {
                val consumption = consumptions[0]
                consumptions.removeAt(0)
                yield(consumption)
            }
        }

    fun collectChargingPoints() =
        sequence<ChargingPoints> {
            while (chargingPoints.isNotEmpty()) {
                val charging = chargingPoints[0]
                chargingPoints.removeAt(0)
                yield(charging)
            }
        }
}
