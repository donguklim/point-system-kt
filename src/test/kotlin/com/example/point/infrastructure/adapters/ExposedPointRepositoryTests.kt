package com.example.point.infrastructure.adapters

import com.example.point.domain.user.errors.DuplicateCodeError
import com.example.point.domain.user.models.ChargedPoints
import com.example.point.domain.user.models.Consumption
import com.example.point.domain.valueObjects.ChargingPoints
import com.example.point.infrastructure.TestDatabase
import com.example.point.infrastructure.database.PointDetails
import com.example.point.infrastructure.database.PointEvents
import com.example.point.infrastructure.database.PointType
import io.github.cdimascio.dotenv.dotenv
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
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun testFetchPoints() {
        val testUserId = 132L
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
    fun testPointFlowAndPointSequencePerformance() {
        val expireDays = 37
        val testUserIds = (333L..358L).toList()
        val userExpectedPoints: MutableMap<Long, Map<Long, Int>> = mutableMapOf()
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

    @Test
    fun testUpdateCharges() {
        val expectedPointsMap =
            (1..10).associate {
                "$it:test" to
                    ChargingPoints(
                        code = "$it:test",
                        numPoints = (1..500).random(),
                        title = "$it-title",

                        description = "${(1..500).random()} some desc",
                    )
            }

        val expiryDays = (30..500).random()
        val repo = ExposedPointRepository(
            expiryDays = expiryDays
        )
        val timeNow = Clock.System.now()
        val transactionAt =
            timeNow.toLocalDateTime(TimeZone.UTC).let {
                LocalDateTime(
                    it.year,
                    it.month,
                    it.dayOfMonth,
                    it.hour,
                    it.minute,
                    it.second,
                    // Mysql datetime only support up to 6 decimal points of second
                    // so discard the last 3 digits of the nanosecond
                    (it.nanosecond / 1000) * 1000,
                )
            }

        val expectedExpireAt =
            timeNow.plus(
                expiryDays,
                DateTimeUnit.DAY,
                TimeZone.UTC,
            ).toLocalDateTime(TimeZone.UTC).let {
                LocalDateTime(
                    it.year,
                    it.month,
                    it.dayOfMonth,
                    it.hour,
                    0,
                    0,
                    0,
                )
            }

        val userId: Long = (1L..10000L).random()
        runBlocking {
            newSuspendedTransaction(Dispatchers.IO) {
                repo.updateCharges(userId, expectedPointsMap.values.toList(), transactionAt)
            }
        }

        val eventIdMap = mutableMapOf<Long, String>()
        transaction {
            PointEvents.selectAll().where(PointEvents.userId eq userId).forEach {
                assertContains(expectedPointsMap, it[PointEvents.transactionCode])
                val expectedPoints = expectedPointsMap[it[PointEvents.transactionCode]]!!

                assertEquals(expectedPoints.numPoints, it[PointEvents.numPoints])
                assertEquals(expectedPoints.title, it[PointEvents.title])
                assertEquals(expectedPoints.description, it[PointEvents.description])
                assertEquals(PointType.CHARGE.value, it[PointEvents.type])
                assertEquals(transactionAt, it[PointEvents.transactionAt])
                eventIdMap[it[PointEvents.id].value] = expectedPoints.code
            }

            PointDetails.selectAll().where(PointDetails.userId eq userId).forEach {
                assertContains(eventIdMap, it[PointDetails.eventId].value)
                val expectedPoints = expectedPointsMap[eventIdMap[it[PointDetails.eventId].value]!!]!!

                assertEquals(expectedPoints.numPoints, it[PointDetails.numPoints])
                assertEquals(PointType.CHARGE.value, it[PointDetails.type])
                assertEquals(it[PointDetails.eventId].value, it[PointDetails.chargeId])
                assertEquals(transactionAt, it[PointDetails.transactionAt])
                assertEquals(expectedExpireAt, it[PointDetails.expireAt])
            }
        }

        transaction {
            PointDetails.deleteAll()
            PointEvents.deleteAll()
        }
    }

    @Test
    fun testUpdateConsumptions() {
        val timeNow = Clock.System.now()
        val transactionAt =
            timeNow.toLocalDateTime(TimeZone.UTC).let {
                LocalDateTime(
                    it.year,
                    it.month,
                    it.dayOfMonth,
                    it.hour,
                    it.minute,
                    it.second,
                    // Mysql datetime only support up to 6 decimal points of second
                    // so discard the last 3 digits of the nanosecond
                    (it.nanosecond / 1000) * 1000,
                )
            }

        val expectedPointsMap =
            (1..10).associate {
                "$it:test:consume" to
                    Consumption(
                        code = "$it:test:consume",
                        cost = (1..500).random(),
                        title = "$it-title",
                        description = "${(1..500).random()} some desc",
                    )
            }

        val totalPoints = expectedPointsMap.values.fold(0) { acc, consumption -> acc + consumption.cost }
        val eachPoints = 100

        val charges =
            (1L..totalPoints / eachPoints + 1).map {
                ChargedPoints(
                    chargeId = it,
                    expireAt =
                        transactionAt.toInstant(TimeZone.UTC).plus(
                            (1..15).random(),
                            DateTimeUnit.HOUR,
                        ).toLocalDateTime(TimeZone.UTC),
                    eachPoints,
                )
            }

        val chargeExpireAtMap: Map<Long, LocalDateTime> = charges.associate { it.chargeId to it.expireAt }
        val chargeUsagesMap: Map<String, MutableMap<Long, Int>> = expectedPointsMap.mapValues { mutableMapOf() }
        var chargeIndex = 0
        expectedPointsMap.values.forEach { consumption ->
            while (consumption.getRemainingCoast() > 0) {
                val numPoints = consumption.consume(charges[chargeIndex])
                chargeUsagesMap[consumption.code]!![charges[chargeIndex].chargeId] = numPoints
                if (charges[chargeIndex].getLeftPoints() == 0) {
                    chargeIndex++
                }
            }
        }

        val repo = ExposedPointRepository()

        val userId: Long = (1L..10000L).random()
        runBlocking {
            newSuspendedTransaction(Dispatchers.IO) {
                repo.updateConsumptions(userId, expectedPointsMap.values.toList(), transactionAt)
            }
        }

        val eventIdMap = mutableMapOf<Long, String>()
        transaction {
            PointEvents.selectAll().where(PointEvents.userId eq userId).forEach {
                assertContains(expectedPointsMap, it[PointEvents.transactionCode])
                val expectedConsumption = expectedPointsMap[it[PointEvents.transactionCode]]!!

                assertEquals(-expectedConsumption.cost, it[PointEvents.numPoints])

                assertEquals(expectedConsumption.title, it[PointEvents.title])
                assertEquals(expectedConsumption.description, it[PointEvents.description])
                assertEquals(PointType.CONSUME.value, it[PointEvents.type])
                assertEquals(transactionAt, it[PointEvents.transactionAt])
                eventIdMap[it[PointEvents.id].value] = expectedConsumption.code
            }

            PointDetails.selectAll().where(PointDetails.userId eq userId).forEach {
                assertContains(eventIdMap, it[PointDetails.eventId].value)
                val expectedConsumption = expectedPointsMap[eventIdMap[it[PointDetails.eventId].value]!!]!!
                val expectedChargeUsages = chargeUsagesMap[expectedConsumption.code]!!
                assertContains(
                    expectedChargeUsages,
                    it[PointDetails.chargeId],
                    "cost: ${expectedConsumption.cost}, usage: ${it[PointDetails.numPoints]}",
                )

                assertEquals(-expectedChargeUsages[it[PointDetails.chargeId]]!!, it[PointDetails.numPoints])
                assertEquals(PointType.CONSUME.value, it[PointDetails.type])
                assertEquals(transactionAt, it[PointDetails.transactionAt])
                assertEquals(chargeExpireAtMap[it[PointDetails.chargeId]], it[PointDetails.expireAt])
            }
        }

        transaction {
            PointDetails.deleteAll()
            PointEvents.deleteAll()
        }
    }

    fun pointTypeArguments(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(PointType.CONSUME),
            Arguments.of(PointType.CHARGE),
            Arguments.of(PointType.REFUND)
        )
    }

    @ParameterizedTest
    @MethodSource("pointTypeArguments")
    fun testDuplicateCodeOnCharge(pointType: PointType) {
        val userId: Long = (1L..10000L).random()
        val duplicateTransactionCode = "test:${(1..10).random()}"

        val transactionAtValue = Clock.System.now().minus(
            322,
            DateTimeUnit.HOUR,
            TimeZone.UTC,
        )

        transaction {
            // test point event
            val testEventId =
                PointEvents.insertAndGetId {
                    it[PointEvents.userId] = userId
                    it[transactionCode] = duplicateTransactionCode
                    it[type] = pointType.value
                    it[numPoints] = if (pointType != PointType.CONSUME) 100 else -100
                    it[title] = "test"
                    it[transactionAt] = transactionAtValue.toLocalDateTime(TimeZone.UTC)
                }

            PointDetails.insertAndGetId {
                it[PointDetails.userId] = userId
                it[PointDetails.eventId] = testEventId
                it[PointDetails.type] = pointType.value
                it[PointDetails.numPoints] = if (pointType != PointType.CONSUME) 100 else -100
                it[PointDetails.chargeId] = testEventId.value
                it[PointDetails.expireAt] = transactionAtValue.plus(
                    100,
                    DateTimeUnit.DAY,
                    TimeZone.UTC,
                ).toLocalDateTime(TimeZone.UTC)
                it[PointDetails.transactionAt] = transactionAtValue.toLocalDateTime(TimeZone.UTC)
            }
        }

        val chargeList = listOf(
            ChargingPoints(
                code=duplicateTransactionCode,
                numPoints = 123,
                title = "another point",
                description = "something"
            )
        )

        val repo = ExposedPointRepository()
        runBlocking {
            newSuspendedTransaction(Dispatchers.IO) {
                val error = assertFailsWith <DuplicateCodeError> {
                    repo.updateCharges(userId, chargeList)
                }

                assertEquals(error.pointType, PointType.CHARGE)
            }
        }

        runBlocking {
            newSuspendedTransaction(Dispatchers.IO) {
                repo.updateCharges(userId + 1, chargeList)
            }
        }

        transaction {
            PointDetails.deleteAll()
            PointEvents.deleteAll()
        }
    }

    @ParameterizedTest
    @MethodSource("pointTypeArguments")
    fun testDuplicateCodeOnConsume(pointType: PointType) {
        val userId: Long = (1L..10000L).random()
        val duplicateTransactionCode = "test:${(1..10).random()}"

        val transactionAtValue = Clock.System.now().minus(
            322,
            DateTimeUnit.HOUR,
            TimeZone.UTC,
        )

        transaction {
            // test point event
            val testEventId =
                PointEvents.insertAndGetId {
                    it[PointEvents.userId] = userId
                    it[transactionCode] = duplicateTransactionCode
                    it[type] = pointType.value
                    it[numPoints] = if (pointType != PointType.CONSUME) 100 else -100
                    it[title] = "test"
                    it[transactionAt] = transactionAtValue.toLocalDateTime(TimeZone.UTC)
                }
        }

        val consumption = Consumption(
            code=duplicateTransactionCode,
            cost = 232,
            title = "another point",
            description = "something"
        )


        val usingPoints = ChargedPoints(
            chargeId =  (1L..10000L).random(),
            expireAt = Clock.System.now().plus(
                322,
                DateTimeUnit.HOUR,
                TimeZone.UTC,
            ).toLocalDateTime(TimeZone.UTC),
            initPoint = consumption.cost
        )
        consumption.consume(usingPoints)

        val consumptionList = listOf(consumption)

        val repo = ExposedPointRepository()
        runBlocking {
            newSuspendedTransaction(Dispatchers.IO) {
                val error = assertFailsWith <DuplicateCodeError> {
                    repo.updateConsumptions(userId, consumptionList)
                }

                assertEquals(error.pointType, PointType.CONSUME)
            }
        }

        runBlocking {
            newSuspendedTransaction(Dispatchers.IO) {
                repo.updateConsumptions(userId + 1, consumptionList)
            }
        }

        transaction {
            PointDetails.deleteAll()
            PointEvents.deleteAll()
        }
    }
}
