package com.example.point.domain.user

import com.example.point.domain.events.NotEnoughPointEvent
import com.example.point.domain.user.models.User
import com.example.point.domain.valueObjects.ChargedPoints
import com.example.point.domain.valueObjects.Consumption
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.*

fun getPointIterator(points: List<Int>) =
    sequence {
        val chargeID = (1..10323).random()

        for (point in points) {
            yield(ChargedPoints(chargeID, point))
        }
    }

fun providePointListToInsufficientPointTest(): List<Arguments> {
    return listOf(
        Arguments.of(1, emptyList<Int>()),
        Arguments.of(3, emptyList<Int>()),
        Arguments.of(1, listOf(1, 3)),
        Arguments.of(7, listOf(1, 3, 7, 38, 22, 1023)),
    )
}

fun providePointListToConsumptionTest(): List<Arguments> {
    return listOf(
        Arguments.of(1, listOf(1, 2, 4), 1),
        Arguments.of(3, listOf(4, 7, 9), 1),
        Arguments.of(6, listOf(4, 7, 9), 2),
        Arguments.of(100, listOf(1, 3, 77, 1, 1, 30), 6),
        Arguments.of(700, listOf(1, 3, 7, 38, 22, 1023, 222), 6),
    )
}

class PointConsumptionTests {
    @ParameterizedTest
    @MethodSource("com.example.point.domain.user.PointConsumptionTestsKt#providePointListToInsufficientPointTest")
    fun testInsufficientPoint(
        additionalPoints: Int,
        pointsList: List<Int>,
    ) {
        val user =
            User(
                (3..23233).random(),
                pointsIter = getPointIterator(pointsList),
            )

        val consumingItem =
            Consumption(
                code = "some_1232",
                cost = pointsList.sum() + additionalPoints,
                productCode = "some_code",
            )
        assertFalse(user.usePoints(consumingItem))

        val events = user.collectEvents().toList()
        assertEquals(1, events.size)

        assertIs<NotEnoughPointEvent>(events[0])

        assertEquals(0, user.collectConsumptions().toList().size)
    }

    @ParameterizedTest
    @MethodSource("com.example.point.domain.user.PointConsumptionTestsKt#providePointListToConsumptionTest")
    fun testSufficientPoint(
        cost: Int,
        pointsList: List<Int>,
        expectedNumUsages: Int,
    )  {
        val user =
            User(
                (3..23233).random(),
                pointsIter = getPointIterator(pointsList),
            )

        val consumingItem =
            Consumption(
                code = "some_1232",
                cost = cost,
                productCode = "some_code",
            )
        assertTrue(user.usePoints(consumingItem))

        assertEquals(0, user.collectEvents().toList().size)

        val consumptions = user.collectConsumptions().toList()
        assertEquals(1, consumptions.size)
        val consumption = consumptions[0]
        assertEquals(consumption.getRemainingCoast(), 0)
        val usages = consumption.collectUsedCharges()
        assertEquals(expectedNumUsages, usages.size)
        assertEquals(cost, usages.sumOf { usage -> usage.points })
    }
}
