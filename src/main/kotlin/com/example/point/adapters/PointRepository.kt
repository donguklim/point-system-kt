package com.example.point.adapters

import com.example.point.domain.user.models.ChargedPoints
import com.example.point.domain.user.models.Consumption
import com.example.point.domain.user.models.User
import com.example.point.domain.valueObjects.ChargingPoints
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDateTime

interface PointRepository {
    fun getPointSeq(
        userId: Long,
        expireAtThreshold: LocalDateTime? = null
    ): Sequence<ChargedPoints> {
        return sequence {}
    }

    fun getPointFlow(
        userId: Long,
        expireAtThreshold: LocalDateTime? = null
    ): Flow<ChargedPoints>

    suspend fun getPointSum(
        userId: Long,
        expireAtThreshold: LocalDateTime? = null
    ): Int

    suspend fun updateCharges(
        userId: Long,
        chargingPointsList: List<ChargingPoints>,
        transactionAt: LocalDateTime? = null,
    )

    suspend fun updateConsumptions(
        userId: Long,
        consumptions: List<Consumption>,
        transactionAt: LocalDateTime? = null,
    )

    fun getUser(
        userId: Long,
        expireAtThreshold: LocalDateTime? = null
    ): User {
        return User(userId, getPointSeq(userId, expireAtThreshold))
    }
}
