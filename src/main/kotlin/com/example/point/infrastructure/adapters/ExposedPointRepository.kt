package com.example.point.infrastructure.adapters

import com.example.point.adapters.PointRepository
import com.example.point.domain.Constants.POINT_EXPIRY_DAYS
import com.example.point.domain.user.errors.DuplicateCodeError
import com.example.point.domain.user.models.ChargedPoints
import com.example.point.domain.user.models.Consumption
import com.example.point.domain.valueObjects.ChargingPoints
import com.example.point.infrastructure.database.PointDetails
import com.example.point.infrastructure.database.PointEvents
import com.example.point.infrastructure.database.PointType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import java.sql.SQLIntegrityConstraintViolationException
import kotlin.sequences.Sequence

class ExposedPointRepository(
    private val expiryDays: Int = POINT_EXPIRY_DAYS,
    private val chunkSize: Int = 2000,
) : PointRepository {
    override fun getPointFlow(
        userId: Long,
        expireAtThreshold: LocalDateTime?
    ): Flow<ChargedPoints> =
        flow {
            val threshold = expireAtThreshold ?: Clock.System.now().toLocalDateTime(TimeZone.UTC)
            val pointSum = PointDetails.numPoints.sum().alias("point_sum")

            PointDetails.select(
                pointSum,
                PointDetails.chargeId,
                PointDetails.expireAt,
            ).where(
                (PointDetails.userId eq userId) and (PointDetails.expireAt greater threshold),
            ).groupBy(
                PointDetails.expireAt,
                PointDetails.chargeId,
            ).orderBy(
                PointDetails.expireAt to SortOrder.ASC,
                PointDetails.chargeId to SortOrder.ASC,
            ).filter {
                (it[pointSum] ?: 0) > 0
            }.forEach {
                emit(ChargedPoints(it[PointDetails.chargeId], it[PointDetails.expireAt], it[pointSum]!!))
            }
        }.buffer(chunkSize)

    override fun getPointSeq(
        userId: Long,
        expireAtThreshold: LocalDateTime?
    ): Sequence<ChargedPoints> =
        sequence {
            val pointSum = PointDetails.numPoints.sum().alias("point_sum")
            val threshold = expireAtThreshold ?: Clock.System.now().toLocalDateTime(TimeZone.UTC)

            PointDetails.select(
                pointSum,
                PointDetails.chargeId,
                PointDetails.expireAt,
            ).where(
                (PointDetails.userId eq userId) and (PointDetails.expireAt greater threshold),
            ).groupBy(
                PointDetails.expireAt,
                PointDetails.chargeId,
            ).orderBy(
                PointDetails.expireAt to SortOrder.ASC,
                PointDetails.chargeId to SortOrder.ASC,
            ).filter {
                (it[pointSum] ?: 0) > 0
            }.forEach {
                yield(ChargedPoints(it[PointDetails.chargeId], it[PointDetails.expireAt], it[pointSum]!!))
            }
        }

    override suspend fun getPointSum(userId: Long, expireAtThreshold: LocalDateTime?): Int {
        val pointSum = PointDetails.numPoints.sum()
        val threshold = expireAtThreshold ?: Clock.System.now().toLocalDateTime(TimeZone.UTC)

        return PointDetails.select(pointSum).where {
            (PointDetails.userId eq userId) and (PointDetails.expireAt greater threshold)
        }.first()[pointSum] ?: 0
    }

    override suspend fun updateCharges(
        userId: Long,
        chargingPointsList: List<ChargingPoints>,
        transactionAt: LocalDateTime?,
    ) {
        val timeNow = Clock.System.now()
        val transactionAtInstant = (transactionAt?.toInstant(TimeZone.UTC)) ?: timeNow
        // expiry time unit is hour
        val expireAt =
            transactionAtInstant.plus(
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
        val nonNullTransactionAt = transactionAt ?: transactionAtInstant.toLocalDateTime(TimeZone.UTC)
        val eventInsertResult = runCatching {
            PointEvents.batchInsert(chargingPointsList) { points ->
                this[PointEvents.userId] = userId
                this[PointEvents.transactionCode] = points.code
                this[PointEvents.numPoints] = points.numPoints
                this[PointEvents.type] = PointType.CHARGE.value
                this[PointEvents.title] = points.title
                this[PointEvents.description] = points.description
                this[PointEvents.transactionAt] = nonNullTransactionAt
            }.map { it[PointEvents.id].value }
        }.onFailure {
            when (val original = (it as? ExposedSQLException)?.cause?.cause) {
                is SQLIntegrityConstraintViolationException ->{
                    val errorMessage = original.message?.lowercase()
                    if (errorMessage?.contains("duplicate entry") == true
                        && errorMessage.contains("transaction_code")) {

                        throw DuplicateCodeError(userId, PointType.CHARGE, errorMessage)
                    }
                }
            }

            throw it
        }.onSuccess {
            PointDetails.batchInsert(it.zip(chargingPointsList)) { (eventId, points) ->
                this[PointDetails.eventId] = eventId
                this[PointDetails.userId] = userId
                this[PointDetails.type] = PointType.CHARGE.value
                this[PointDetails.numPoints] = points.numPoints
                // use event id as charge id
                this[PointDetails.chargeId] = eventId
                this[PointDetails.transactionAt] = nonNullTransactionAt
                this[PointDetails.expireAt] = expireAt
            }
        }
    }

    override suspend fun updateConsumptions(
        userId: Long,
        consumptions: List<Consumption>,
        transactionAt: LocalDateTime?,
    ) {
        val nonNullTransactionAt = transactionAt ?: Clock.System.now().toLocalDateTime(TimeZone.UTC)

        val eventInsertResult = runCatching {
            PointEvents.batchInsert(consumptions) { consumption ->
                this[PointEvents.userId] = userId
                this[PointEvents.transactionCode] = consumption.code
                this[PointEvents.numPoints] = -consumption.cost
                this[PointEvents.type] = PointType.CONSUME.value
                this[PointEvents.title] = consumption.title
                this[PointEvents.description] = consumption.description
                this[PointEvents.transactionAt] = nonNullTransactionAt
            }.map { it[PointEvents.id].value }
        }.onFailure {
            when (val original = (it as? ExposedSQLException)?.cause?.cause) {
                is SQLIntegrityConstraintViolationException ->{
                    val errorMessage = original.message?.lowercase()
                    if (errorMessage?.contains("duplicate entry") == true
                        && errorMessage.contains("transaction_code")) {

                        throw DuplicateCodeError(userId, PointType.CONSUME, errorMessage)
                    }
                }
            }
        }.onSuccess {
            val eventIdUsagePairs =
                it.zip(consumptions).map { (eventId, consumption) ->
                    consumption.collectUsedCharges().map { Pair(eventId, it) }
                }.flatten()

            PointDetails.batchInsert(eventIdUsagePairs) { (eventId, usage) ->
                this[PointDetails.eventId] = eventId
                this[PointDetails.userId] = userId
                this[PointDetails.type] = PointType.CONSUME.value
                this[PointDetails.numPoints] = -usage.points
                // use event id as charge id
                this[PointDetails.chargeId] = usage.chargeId
                this[PointDetails.transactionAt] = nonNullTransactionAt
                this[PointDetails.expireAt] = usage.expireAt
            }
        }
    }
}
