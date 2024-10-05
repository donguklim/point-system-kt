package com.example.point.adapters

import com.example.point.domain.user.models.User
import com.example.point.domain.valueObjects.ChargedPoints
import com.example.point.domain.valueObjects.ChargingPoints
import com.example.point.domain.valueObjects.Consumption
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDateTime

interface PointRepository {
    fun getPointSeq(userId: Long): Sequence<ChargedPoints> {
        return sequence {}
    }

    fun getPointFlow(userId: Long): Flow<ChargedPoints>

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

    fun getUser(userId: Long): User {
        return User(userId, getPointSeq(userId))
    }
}
