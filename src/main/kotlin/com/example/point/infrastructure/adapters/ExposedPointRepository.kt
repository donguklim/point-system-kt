package com.example.point.infrastructure.adapters

import com.example.point.adapters.PointRepository
import com.example.point.domain.DomainConstants.START_TIMESTAMP
import com.example.point.domain.valueObjects.ChargedPoints
import com.example.point.domain.valueObjects.ChargingPoints
import com.example.point.domain.valueObjects.Consumption
import com.example.point.infrastructure.database.PointDetails
import com.example.point.infrastructure.database.PointEvents
import com.example.point.infrastructure.database.PointType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import kotlin.sequences.Sequence

class ExposedPointRepository(
    private val expiryDays: Int,
    private val chunkSize: Int = 2000,
) : PointRepository {
    override fun getPointFlow(userId: Int): Flow<ChargedPoints> =
        flow {
            var thresholdDateTime =
                Clock.System.now().minus(
                    expiryDays,
                    DateTimeUnit.DAY,
                    TimeZone.UTC,
                ).toLocalDateTime(TimeZone.UTC)

            val pointSum = PointDetails.numPoints.sum().alias("point_sum")

            PointDetails.select(
                pointSum,
                PointDetails.chargeId,
                PointDetails.expireAt,
            ).where(
                (PointDetails.userId eq userId) and (PointDetails.expireAt greater thresholdDateTime),
            ).groupBy(
                PointDetails.expireAt,
                PointDetails.chargeId,
            ).orderBy(
                PointDetails.expireAt to SortOrder.ASC,
                PointDetails.chargeId to SortOrder.ASC,
            ).filter {
                (it[pointSum] ?: 0) > 0
            }.forEach {
                emit(ChargedPoints(it[PointDetails.chargeId], it[pointSum]!!))
            }
        }.buffer(chunkSize)

    override fun getPointSeq(userId: Int): Sequence<ChargedPoints> =
        sequence {
            val pointSum = PointDetails.numPoints.sum().alias("point_sum")
            var thresholdDateTime =
                Clock.System.now().minus(
                    expiryDays,
                    DateTimeUnit.DAY,
                    TimeZone.UTC,
                ).toLocalDateTime(TimeZone.UTC)

            PointDetails.select(
                pointSum,
                PointDetails.chargeId,
                PointDetails.expireAt,
            ).where(
                (PointDetails.userId eq userId) and (PointDetails.expireAt greater thresholdDateTime),
            ).groupBy(
                PointDetails.expireAt,
                PointDetails.chargeId,
            ).orderBy(
                PointDetails.expireAt to SortOrder.ASC,
                PointDetails.chargeId to SortOrder.ASC,
            ).filter {
                (it[pointSum] ?: 0) > 0
            }.forEach {
                yield(ChargedPoints(it[PointDetails.chargeId], it[pointSum]!!))
            }
        }

    override suspend fun updateCharges(
        userId: Int,
        charingPoints: List<ChargingPoints>,
        transactionAt: LocalDateTime?,
    ) {
        val timeNow = Clock.System.now()
        val transactionAtInstant = (transactionAt?.toInstant(TimeZone.UTC)) ?: timeNow
        val expireAt =
            transactionAtInstant.plus(
                expiryDays,
                DateTimeUnit.DAY,
                TimeZone.UTC,
            ).toLocalDateTime(TimeZone.UTC)
        val nonNullTransactionAt = transactionAt ?: transactionAtInstant.toLocalDateTime(TimeZone.UTC)

        val eventIds = PointEvents.batchInsert(charingPoints) { point ->
            this[PointEvents.userId] = userId
            this[PointEvents.transactionCode] = point.code
            this[PointEvents.type] = PointType.CHARGE.value
            this[PointEvents.title] = point.title
            this[PointEvents.description] = point.description
            this[PointEvents.transactionAt] = nonNullTransactionAt
        }.map { it[PointEvents.id].value }


        PointDetails.batchInsert(eventIds.zip(charingPoints)) { (eventId, point) ->
            this[PointDetails.eventId] = eventId
            this[PointDetails.userId] = userId
            this[PointDetails.type] = PointType.CHARGE.value
            this[PointDetails.numPoints] = point.numPoints
            // use event id as charge id
            this[PointDetails.chargeId] = eventId
            this[PointDetails.expireAt] = expireAt
        }

        delay((charingPoints.size / 10).toLong())

    }

    override suspend fun updateConsumptions(
        userId: Int,
        consumption: List<Consumption>,
        transactionAt: LocalDateTime?,
    ) {
    }
}
