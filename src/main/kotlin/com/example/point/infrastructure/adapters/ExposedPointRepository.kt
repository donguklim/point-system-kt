package com.example.point.infrastructure.adapters

import com.example.point.adapters.PointRepository
import com.example.point.domain.valueObjects.ChargedPoints
import com.example.point.domain.valueObjects.ChargingPoints
import com.example.point.domain.valueObjects.Consumption
import com.example.point.infrastructure.database.PointDetails
import kotlinx.coroutines.CoroutineScope
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
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.kotlin.datetime.KotlinLocalDateTimeColumnType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.transactions.TransactionManager
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

//            PointDetails.select(
//                pointSum,
//                PointDetails.chargeId,
//                PointDetails.expireAt,
//            ).where(
//                (PointDetails.userId eq userId) and (PointDetails.expireAt greater thresholdDateTime)
//            ).forUpdate().groupBy(
//                PointDetails.expireAt,
//                PointDetails.chargeId,
//            ).orderBy(
//                PointDetails.expireAt to SortOrder.ASC,
//                PointDetails.chargeId to SortOrder.ASC,
//            ).filter{
//                (it[pointSum] ?: 0) > 0
//            }.forEach {
//                emit(ChargedPoints(it[PointDetails.chargeId], it[pointSum]!!))
//            }

            val connection = TransactionManager.current().connection

            // Create a statement
            val statement = connection.prepareStatement(
                "SELECT sum(num_points) as point_sum, charge_id, expire_at FROM point_details " +
                        "where user_id = ? and expire_at > ? group by expire_at, charge_id " +
                        "order by expire_at, charge_id asc",
                false
            )

            statement.fillParameters(
                listOf(
                    Pair(IntegerColumnType(), userId),
                    Pair(KotlinLocalDateTimeColumnType(), thresholdDateTime)
                )
            );

            // Set the fetch size
            statement.fetchSize = chunkSize

            // Execute your query
            val resultSet = statement.executeQuery()

            do {
                var curChunkSize = 0
                val awaitingPoints = coroutineScope {
                    async {
                        val points: MutableList<ChargedPoints> = mutableListOf()
                        while (curChunkSize < chunkSize && resultSet.next()) {
                            val point_sum =  resultSet.getInt("point_sum")
                            curChunkSize++
                            if (point_sum > 0) points.add(ChargedPoints(resultSet.getInt("charge_id"), point_sum))
                        }
                        points
                    }
                }

                awaitingPoints.await().forEach { emit(it) }

            } while (curChunkSize == chunkSize)

//            while (resultSet.next()) {
//                val point_sum =  resultSet.getInt("point_sum")
//                if (point_sum > 0) emit(ChargedPoints(resultSet.getInt("charge_id"), point_sum))
//            }

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

//            PointDetails.select(
//                pointSum,
//                PointDetails.chargeId,
//                PointDetails.expireAt,
//            ).where(
//                (PointDetails.userId eq userId) and (PointDetails.expireAt greater thresholdDateTime)
//            ).forUpdate().groupBy(
//                PointDetails.expireAt,
//                PointDetails.chargeId,
//            ).orderBy(
//                PointDetails.expireAt to SortOrder.ASC,
//                PointDetails.chargeId to SortOrder.ASC,
//            ).filter{
//                (it[pointSum] ?: 0) > 0
//            }.forEach {
//                yield(ChargedPoints(it[PointDetails.chargeId], it[pointSum]!!))
//            }


            val connection = TransactionManager.current().connection

            // Create a statement
            val statement = connection.prepareStatement(
                "SELECT sum(num_points) as point_sum, charge_id, expire_at FROM point_details " +
                        "where user_id = ? and expire_at > ? group by expire_at, charge_id " +
                        "order by expire_at, charge_id asc",
                false
            )

            statement.fillParameters(
                listOf(
                    Pair(IntegerColumnType(), userId),
                    Pair(KotlinLocalDateTimeColumnType(), thresholdDateTime)
                )
            );

            // Set the fetch size
            statement.fetchSize = chunkSize

            // Execute your query
            val resultSet = statement.executeQuery()

            while (resultSet.next()) {
                val point_sum =  resultSet.getInt("point_sum")
                if (point_sum > 0) yield(ChargedPoints(resultSet.getInt("charge_id"), point_sum))
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
