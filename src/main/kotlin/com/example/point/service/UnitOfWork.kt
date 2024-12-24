package com.example.point.service

import com.example.point.adapters.PointCache
import com.example.point.adapters.PointRepository
import com.example.point.domain.user.models.User

abstract class UnitOfWork(
    private val repository: PointRepository,
    val pointCache: PointCache
) {

    suspend fun begin(userId: Long) : User {
        val pointUser =  repository.getUser(userId, pointCache.getUserValidExpiryThreshold(userId))
        return pointUser
    }

    suspend fun commit(user: User) {
        repository.updateCharges(user.userId, user.collectChargingPoints().toList())
        repository.updateConsumptions(user.userId, user.collectConsumptions().toList())
    }

    suspend fun end(user: User) {
        commit(user)
    }

    abstract suspend fun userUnit(userId: Long, unitLambda: suspend UnitOfWork.() -> Unit)

    suspend inline fun userAction(
        userId: Long,
        crossinline lambda: suspend UnitOfWork.(User) -> Unit,
    ) {
        userUnit(userId) {
            val pointUser = this.begin(userId)
            this.lambda(pointUser)
            this.end(pointUser)
        }
    }
}
