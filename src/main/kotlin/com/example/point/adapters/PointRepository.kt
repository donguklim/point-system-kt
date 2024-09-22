package com.example.point.adapters

import com.example.point.domain.user.models.User
import com.example.point.domain.valueObjects.ChargedPoints
import com.example.point.domain.valueObjects.ChargingPoints
import com.example.point.domain.valueObjects.Consumption
import kotlinx.coroutines.flow.Flow

interface PointRepository {
    fun getPointSeq(userId: Int): Sequence<ChargedPoints> {
        return sequence {}
    }

    fun getPointFlow(userId: Int): Flow<ChargedPoints>

    suspend fun updateCharges(
        userId: Int,
        chargedPoint: List<ChargingPoints>,
    )

    suspend fun updateConsumptions(
        userId: Int,
        consumption: List<Consumption>,
    )

    fun getUser(userId: Int): User {
        return User(userId, getPointSeq(userId))
    }
}
