package com.example.point.adapters

import com.example.point.domain.valueObjects.ChargedPoints
import com.example.point.domain.valueObjects.ChargingPoints
import com.example.point.domain.valueObjects.Consumption

interface PointRepository {
    fun getPointSeq (userId: Int): Sequence<ChargedPoints>
    suspend fun updateCharge(userId: Int, chargedPoint: ChargingPoints)
    suspend fun updateConsumption(userId: Int, consumption: Consumption)
}