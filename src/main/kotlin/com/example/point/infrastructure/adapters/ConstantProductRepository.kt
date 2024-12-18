package com.example.point.infrastructure.adapters

import com.example.point.adapters.ProductRepository
import com.example.point.domain.Constants
import com.example.point.domain.user.models.Consumption

class ConstantProductRepository: ProductRepository {
    private val USER_UNIQUE_PRODUCT_PREFIX =  "unique"
    private val productCONSUMPTIONPOINTS = mapOf(
        "unique.1" to 500,
        "unique.2" to 400,
        "unique.3" to 100,
        "pr.1" to 100,
        "pr.3" to 350,
        "pr.4" to 400,
    )

    override  fun getConsumptionByProductCode(productCode: String): Consumption? {
        val relativeTimestamp =
            System.currentTimeMillis() / (
                    1000 / Constants.TIME_RECORDING_SCALE
                    ) - Constants.SCALED_START_TIMESTAMP

        val cost = productCONSUMPTIONPOINTS[productCode] ?: return null
        val code = if (productCode.startsWith(USER_UNIQUE_PRODUCT_PREFIX)) String.format(
            Constants.UNIQUE_USER_PRODUCT_PURCHASE_CODE_FORMAT,
            productCode,
        ) else String.format(
            Constants.PRODUCT_PURCHASE_CODE_FORMAT,
            productCode,
            relativeTimestamp
        )

        return Consumption(
            code = code,
            cost = cost,
            title = "purchase $productCode",
            description = "purchased item $productCode",
        )
    }
}