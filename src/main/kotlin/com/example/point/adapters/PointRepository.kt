package com.example.point.adapters

import com.example.point.domain.user.models.User
import com.example.point.domain.valueObjects.ChargedPoints
import com.example.point.domain.valueObjects.ChargingPoints
import com.example.point.domain.valueObjects.Consumption
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDateTime

interface PointRepository {
    fun getPointSeq(userId: Int): Sequence<ChargedPoints> {
        return sequence {}
    }

    fun getPointFlow(userId: Int): Flow<ChargedPoints>

    suspend fun updateCharges(
        userId: Int,
        chargingPoints: List<ChargingPoints>,
        transactionAt: LocalDateTime? = null,
    )

    suspend fun updateConsumptions(
        userId: Int,
        consumption: List<Consumption>,
        transactionAt: LocalDateTime? = null,
    )

    fun getUser(userId: Int): User {
        return User(userId, getPointSeq(userId))
    }
}
