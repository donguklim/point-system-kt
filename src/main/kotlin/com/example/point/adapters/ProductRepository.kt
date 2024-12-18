package com.example.point.adapters

import com.example.point.domain.user.models.Consumption

interface ProductRepository {
    fun getConsumptionByProductCode(productCode: String): Consumption?
}
