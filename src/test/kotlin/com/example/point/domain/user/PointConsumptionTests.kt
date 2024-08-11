package com.example.point.domain.user

import kotlin.test.assertIs

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

import com.example.point.domain.user.models.User
import com.example.point.domain.user.valueObjects.Consumption
import com.example.point.domain.user.valueObjects.ChargedPoints
import com.example.point.domain.user.events.NotEnoughPointEvent

fun getPointIterator(points: List<Int>) = sequence {
    val chargeID = (1..10323).random()

    for(point in points) {
        yield(ChargedPoints(chargeID, point))
    }

}

class PointConsumptionTests {

    @Test
    fun testInsufficientPoint() {

        val pointList = listOf(3, 4, 3, 5, 7)

        var user = User(
            (3.. 23233).random(),
            pointsIter = getPointIterator(pointList)
        )

        val consumingItem = Consumption(
            code = "some_1232",
            cost = pointList.sum() + 1,
            productCode = "some_code"
        )
        assertFalse(user.usePoints(consumingItem))

        val events = user.collectEvents().toList()
        assertEquals(events.size, 1)

        assertIs<NotEnoughPointEvent>(events[0])

    }

}