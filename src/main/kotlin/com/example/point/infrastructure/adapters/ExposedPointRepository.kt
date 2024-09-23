package com.example.point.infrastructure.adapters

import com.example.point.adapters.PointRepository
import com.example.point.domain.valueObjects.ChargedPoints
import com.example.point.domain.valueObjects.ChargingPoints
import com.example.point.domain.valueObjects.Consumption
import com.example.point.infrastructure.database.PointDetails
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.sum
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
            var thresholdChargeId = 0

            val pointSum = PointDetails.numPoints.sum().alias("point_sum")

            PointDetails.select(PointDetails.id).where(
                (PointDetails.userId eq userId) and (PointDetails.expireAt greater thresholdDateTime),
            ).forUpdate()

            do {
                var count = 0
                val awaitingPoints =
                    coroutineScope {
                        async {
                            PointDetails.select(
                                pointSum,
                                PointDetails.chargeId,
                                PointDetails.expireAt,
                            ).where(
                                (PointDetails.userId eq userId)
                                    and (
                                        (PointDetails.expireAt greater thresholdDateTime)
                                            or (
                                                (PointDetails.expireAt eq thresholdDateTime)
                                                    and (PointDetails.chargeId greater thresholdChargeId)
                                            )
                                    ),
                            ).groupBy(
                                PointDetails.expireAt,
                                PointDetails.chargeId,
                            ).orderBy(
                                PointDetails.expireAt to SortOrder.ASC,
                                PointDetails.chargeId to SortOrder.ASC,
                            ).limit(chunkSize).map {
                                Pair(ChargedPoints(it[PointDetails.chargeId], it[pointSum] ?: 0), it[PointDetails.expireAt])
                            }
                        }
                    }

                awaitingPoints.await().forEach {
                    val (point, expireAt) = it
                    thresholdDateTime = expireAt
                    thresholdChargeId = point.chargeId
                    count++
                    if (point.getLeftPoints() > 0) {
                        emit(point)
                    }
                }
            } while (count == chunkSize)
        }

    override fun getPointSeq(userId: Int): Sequence<ChargedPoints> =
        sequence {
            val pointSum = PointDetails.numPoints.sum().alias("point_sum")
            var thresholdDateTime =
                Clock.System.now().minus(
                    expiryDays,
                    DateTimeUnit.DAY,
                    TimeZone.UTC,
                ).toLocalDateTime(TimeZone.UTC)

            var thresholdChargeId = 0

            PointDetails.select(PointDetails.id).where(
                (PointDetails.userId eq userId) and (PointDetails.expireAt greater thresholdDateTime),
            ).forUpdate()

            do {
                var count = 0
                PointDetails.select(
                    pointSum,
                    PointDetails.chargeId,
                    PointDetails.expireAt,
                ).where(
                    (PointDetails.userId eq userId)
                        and (
                            (PointDetails.expireAt greater thresholdDateTime)
                                or (
                                    (PointDetails.expireAt eq thresholdDateTime)
                                        and (PointDetails.chargeId greater thresholdChargeId)
                                )
                        ),
                ).groupBy(
                    PointDetails.expireAt,
                    PointDetails.chargeId,
                ).orderBy(
                    PointDetails.expireAt to SortOrder.ASC,
                    PointDetails.chargeId to SortOrder.ASC,
                ).limit(chunkSize).forEach {
                    thresholdDateTime = it[PointDetails.expireAt]
                    thresholdChargeId = it[PointDetails.chargeId]
                    count++
                    if ((it[pointSum] ?: 0) > 0) {
                        yield(ChargedPoints(it[PointDetails.chargeId], it[pointSum]!!))
                    }
                }
            } while (count == chunkSize)
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
