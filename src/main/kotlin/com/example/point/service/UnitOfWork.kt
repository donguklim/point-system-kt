package com.example.point.service

import com.example.point.adapters.PointCache
import com.example.point.adapters.PointRepository
import com.example.point.domain.user.models.User

abstract class UnitOfWork(
    private val repository: PointRepository,
    val pointCache: PointCache
) {
    var user: User? = null

    suspend fun begin(userId: Long) : User {
        val pointUser =  repository.getUser(userId, pointCache.getUserValidExpiryThreshold(userId))
        user = pointUser
        return pointUser
    }

    suspend fun commit() {
        user?.let {
            repository.updateCharges(it.userId, it.collectChargingPoints().toList())
            repository.updateConsumptions(it.userId, it.collectConsumptions().toList())
        }
    }

    suspend fun end() {
        commit()
    }

    abstract suspend fun userUnit(userId: Long, unitLambda: suspend UnitOfWork.() -> Unit)

    suspend inline fun userAction(
        userId: Long,
        crossinline lambda: suspend UnitOfWork.(User) -> Unit,
    ) {
        userUnit(userId) {
            val pointUser = this.begin(userId)
            this.lambda(pointUser)
            this.end()
        }
    }
}
