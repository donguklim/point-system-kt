package com.example.point.infrastructure.adapters

import com.example.point.infrastructure.TestDatabase
import com.example.point.infrastructure.database.PointDetails
import com.example.point.infrastructure.database.PointEvents
import com.example.point.infrastructure.database.PointType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertContains
import kotlin.test.assertTrue
import kotlin.time.measureTime

data class PointData(
    val chargeId: Long,
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
        val charges: Map<Long, List<Int>> =
            mapOf(
                12343L to listOf(100, -20, -70),
                11222L to listOf(3, -1, -1),
                9333L to listOf(1),
                8343L to listOf(3, -3),
                5222L to listOf(33, -16, -17),
            )
        val expireDays = 37

        val chargePointSums: Map<Long, Int> =
            charges.mapValues {
                it.value.sum()
            }

        // charges should not have negative sum
        chargePointSums.forEach { assertTrue(it.value >= 0) }
        val expectedPoints = chargePointSums.filter { it.value > 0 }

        val expiredCharges: Map<Long, List<Int>> =
            mapOf(
                8L to listOf(1232, -203, -730),
                7L to listOf(3, -1, -1),
                5L to listOf(10, -10),
                3L to listOf(300, -3),
            )

        // expired and not expired charge data charge id should not intersect
        assertTrue(charges.keys.intersect(expiredCharges.keys).isEmpty())

        val expiredPointDataList =
            expiredCharges.map {
                val numHours = (1..19).random()
                val expireAt =
                    Clock.System.now().minus(
                        numHours,
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
                                expireDays,
                                DateTimeUnit.DAY,
                                TimeZone.UTC,
                            ).toLocalDateTime(TimeZone.UTC),
                    )
                }
            }.flatten()

        val validPointDataList =
            charges.map {
                val numHours = (0..50).random()
                val expireAt =
                    Clock.System.now().plus(
                        numHours,
                        DateTimeUnit.HOUR,
                        TimeZone.UTC,
                    ).plus(
                        10,
                        DateTimeUnit.MINUTE,
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
                                expireDays,
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
                    it[numPoints] = 100
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
                    "The map should contain the key '${charge.chargeId}', ${charge.expireAt}, ${Clock.System.now().toLocalDateTime(
                        TimeZone.UTC,
                    )}",
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

    @Test
    fun pointFlow() {
        val expireDays = 37
        val testUserIds = (333..358).toList()
        val userExpectedPoints: MutableMap<Int, Map<Long, Int>> = mutableMapOf()
        for (testUserId in testUserIds) {
            val charges: Map<Long, List<Int>> =
                (1L..20L).associate {
                    it to (List(11) { -1 } + listOf(11 + (0..20).random()))
                }

            val chargePointSums: Map<Long, Int> =
                charges.mapValues {
                    it.value.sum()
                }

            chargePointSums.forEach { assertTrue(it.value >= 0) }
            val expectedPoints = chargePointSums.filter { it.value > 0 }
            userExpectedPoints[testUserId] = expectedPoints

            val expiredCharges: Map<Long, List<Int>> =
                mapOf(
                    3008L to listOf(1232 + (1..50).random(), -203, -730),
                    3007L to listOf(3 + (1..50).random(), -1, -1),
                    3005L to listOf(10 + (1..50).random(), -10),
                    3003L to listOf(300 + (1..50).random(), -3),
                )

            assertTrue(charges.keys.intersect(expiredCharges.keys).isEmpty())

            val expiredPointDataList =
                expiredCharges.map {
                    val numDays = (1..19).random()
                    val expireAt =
                        Clock.System.now().minus(
                            numDays,
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
                                    expireDays,
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
                        Clock.System.now().plus(
                            numDays,
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
                                    expireDays,
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
                        it[transactionCode] = "test:$testUserId"
                        it[type] = PointType.CHARGE.value
                        it[numPoints] = 100
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
        }

        val repo =
            ExposedPointRepository(
                expireDays,
                chunkSize = 2,
            )

        val elaspedSync =
            measureTime {
                for (testUserId in testUserIds) {
                    var chargeCount = 0
                    assertContains(userExpectedPoints, testUserId)
                    val expectedPoints = userExpectedPoints[testUserId]!!
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
                }
            }

        println("Synchronous transaction Elapsed time: $elaspedSync")

        val elapsed =
            measureTime {
                runBlocking {
                    val jobs = mutableListOf<Job>()

                    for (testUserId in testUserIds) {
                        val job =
                            launch {
                                newSuspendedTransaction(Dispatchers.IO) {
                                    // addLogger(StdOutSqlLogger)
                                    assertContains(userExpectedPoints, testUserId)
                                    val expectedPoints = userExpectedPoints[testUserId]!!
                                    var chargeCount = 0

                                    repo.getPointFlow(testUserId).collect { charge ->
                                        chargeCount++
                                    }
                                    assertEquals(expectedPoints.size, chargeCount, "$testUserId does not have expected points in flow")
                                }
                            }
                        jobs.add(job)
                    }

                    jobs.joinAll()
                }
            }
        println("Suspended transaction Elapsed time: $elapsed")

        transaction {
            PointDetails.deleteAll()
            PointEvents.deleteAll()
        }
    }
}
