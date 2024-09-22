package com.example.point.infrastructure.adapters

import com.example.point.adapters.PointRepository
import com.example.point.domain.valueObjects.ChargedPoints
import com.example.point.domain.valueObjects.ChargingPoints
import com.example.point.domain.valueObjects.Consumption
import com.example.point.infrastructure.database.PointDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.coroutines.CoroutineContext

class ExposedPointRepository(
    private val expiryDays: Int,
    private val chargeBufferSize: Int = 100,
) : PointRepository {
    override fun getPointFlow(userId: Int): Flow<ChargedPoints> =
        flow {
            val thresholdDateTime =
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
            ).forUpdate().groupBy(
                PointDetails.expireAt,
                PointDetails.chargeId,
            ).orderBy(PointDetails.expireAt to SortOrder.ASC).filter {
                ((it[pointSum] ?: 0) > 0)
            }.forEach {
                emit(ChargedPoints(it[PointDetails.chargeId], it[pointSum] ?: 0))
            }
        }


    override fun getPointSeq(userId: Int): Sequence<ChargedPoints> {
        val thresholdDateTime =
            Clock.System.now().minus(
                expiryDays,
                DateTimeUnit.DAY,
                TimeZone.UTC,
            ).toLocalDateTime(TimeZone.UTC)

        val pointSum = PointDetails.numPoints.sum().alias("point_sum")

        return sequence {
            PointDetails.select(
                pointSum,
                PointDetails.chargeId,
                PointDetails.expireAt,
            ).where(
                (PointDetails.userId eq userId) and (PointDetails.expireAt greater thresholdDateTime),
            ).forUpdate().groupBy(
                PointDetails.expireAt,
                PointDetails.chargeId,
            ).orderBy(PointDetails.expireAt to SortOrder.ASC).filter {
                ((it[pointSum] ?: 0) > 0)
            }.forEach {
                yield(ChargedPoints(it[PointDetails.chargeId], it[pointSum] ?: 0))
            }
        }
    }

    override suspend fun updateCharges(
        userId: Int,
        chargedPoint: List<ChargingPoints>,
    ) {
    }

    override suspend fun updateConsumptions(
        userId: Int,
        consumption: List<Consumption>,
    ) {
    }
}
