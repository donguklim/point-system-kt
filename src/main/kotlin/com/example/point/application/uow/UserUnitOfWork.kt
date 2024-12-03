package com.example.point.application.uow

import com.example.point.adapters.PointRepository
import com.example.point.domain.user.models.User

abstract class UserUnitOfWork(private val repository: PointRepository) {
    var user: User? = null

    fun begin(userId: Long) {
        user = repository.getUser(userId)
    }
    suspend fun commit() {
        user?.let {
            repository.updateCharges(it.userId, it.collectChargingPoints().toList())
            repository.updateConsumptions(it.userId, it.collectConsumptions().toList())
        }
    }

    suspend fun end() {
        commit()
        user = null
    }

    abstract suspend fun userUnit( unitLambda: suspend UserUnitOfWork.() -> Unit)

    suspend inline fun userAction(
        userId: Long,
        crossinline lambda: UserUnitOfWork.() -> Unit,
    ) {
        userUnit {
            this.begin(userId)
            this.lambda()
            this.end()
        }
    }
}
