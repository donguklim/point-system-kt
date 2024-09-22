package com.example.point.infrastructure.adapters

import com.example.point.infrastructure.TestDatabase
import com.example.point.infrastructure.database.PointDetails
import com.example.point.infrastructure.database.PointEvents
import com.example.point.infrastructure.database.PointType
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertContains
import kotlin.test.assertTrue

data class PointData(
    val chargeId: Int,
    val numPoints: Int,
    val transactionAt: LocalDateTime,
    val expireAt: LocalDateTime,
)

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExposedPointRepositoryTests {
    @BeforeAll
    fun setUp() {
        TestDatabase
    }

    @Test
    fun fetchPoints() {
        val testUserId = 132
        val charges: Map<Int, List<Int>> =
            mapOf(
                12343 to listOf(100, -20, -70),
                11222 to listOf(3, -1, -1),
                9333 to listOf(1),
                8343 to listOf(3, -3),
                5222 to listOf(33, -16, -17),
            )
        val expireDays = 37

        val chargePointSums: Map<Int, Int> =
            charges.mapValues {
                it.value.sum()
            }

        // charges should not have negative sum
        chargePointSums.forEach { assertTrue(it.value >= 0) }
        val expectedPoints = chargePointSums.filter { it.value > 0 }

        val expiredCharges: Map<Int, List<Int>> =
            mapOf(
                8 to listOf(1232, -203, -730),
                7 to listOf(3, -1, -1),
                5 to listOf(10, -10),
                3 to listOf(300, -3),
            )

        // expired and not expired charge data charge id should not intersect
        assertTrue(charges.keys.intersect(expiredCharges.keys).isEmpty())

        val expiredPointDataList =
            expiredCharges.map {
                val numDays = (1..19).random()
                val expireAt =
                    Clock.System.now().minus(
                        expireDays + numDays,
                        DateTimeUnit.DAY,
                        TimeZone.UTC,
                    )
                it.value.map { numPoints ->
                    PointData(
                        chargeId = it.key,
                        numPoints = numPoints,
                        expireAt =
                            expireAt.toLocalDateTime(TimeZone.UTC).let {
                                LocalDateTime(it.year, it.month, it.dayOfMonth, it.hour, 0, 0, 0)
                            },
                        transactionAt =
                            expireAt.minus(
                                (1..9).random(),
                                DateTimeUnit.DAY,
                                TimeZone.UTC,
                            ).toLocalDateTime(TimeZone.UTC),
                    )
                }
            }.flatten()

        val validPointDataList =
            charges.map {
                val numDays = (0..5).random()
                val expireAt =
                    Clock.System.now().minus(
                        expireDays - numDays,
                        DateTimeUnit.DAY,
                        TimeZone.UTC,
                    ).plus(
                        1,
                        DateTimeUnit.HOUR,
                        TimeZone.UTC,
                    )
                it.value.map { numPoints ->
                    PointData(
                        chargeId = it.key,
                        numPoints = numPoints,
                        expireAt =
                            expireAt.toLocalDateTime(TimeZone.UTC).let {
                                LocalDateTime(it.year, it.month, it.dayOfMonth, it.hour, 0, 0, 0)
                            },
                        transactionAt =
                            expireAt.minus(
                                (1..9).random(),
                                DateTimeUnit.DAY,
                                TimeZone.UTC,
                            ).toLocalDateTime(TimeZone.UTC),
                    )
                }
            }.flatten()

        transaction {
            // test point event
            val testEventId =
                PointEvents.insertAndGetId {
                    it[userId] = testUserId
                    it[transactionCode] = "test1"
                    it[type] = PointType.CHARGE.value
                    it[title] = "test"
                    it[transactionAt] = Clock.System.now().toLocalDateTime(TimeZone.UTC)
                }

            PointDetails.batchInsert(expiredPointDataList + validPointDataList) { pointInfo ->
                this[PointDetails.userId] = testUserId
                this[PointDetails.eventId] = testEventId
                this[PointDetails.type] = if (pointInfo.numPoints >= 0) PointType.CHARGE.value else PointType.CONSUME.value
                this[PointDetails.numPoints] = pointInfo.numPoints
                this[PointDetails.chargeId] = pointInfo.chargeId
                this[PointDetails.expireAt] = pointInfo.expireAt
                this[PointDetails.transactionAt] = pointInfo.transactionAt
            }
        }

        val repo =
            ExposedPointRepository(
                expireDays,
            )

        var chargeCount = 0
        transaction {
            for (charge in repo.getPointSeq(testUserId)) {
                assertContains(
                    expectedPoints,
                    charge.chargeId,
                    "The map should contain the key '${charge.chargeId}'",
                )
                assertEquals(expectedPoints[charge.chargeId], charge.getLeftPoints())
                chargeCount++
            }
        }

        assertEquals(expectedPoints.size, chargeCount)

        transaction {
            PointDetails.deleteAll()
            PointEvents.deleteAll()
        }
    }
}
