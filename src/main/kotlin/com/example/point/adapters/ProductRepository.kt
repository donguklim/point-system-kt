package com.example.point.adapters

import com.example.point.domain.user.models.Consumption

interface ProductRepository {
    suspend fun getConsumptionByProductCode(productCode: String): Consumption?
}
