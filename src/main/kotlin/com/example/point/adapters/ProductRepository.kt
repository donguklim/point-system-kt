package com.example.point.adapters

import com.example.point.domain.valueObjects.Consumption

interface ProductRepository {

    suspend fun getConsumptionByProductCode(productCode: String): Consumption?
}