package com.example.point.application.uow

import com.example.point.adapters.PointRepository
import com.example.point.domain.user.models.User

abstract class UserUnitOfWork(private val repository: PointRepository) {
    var user : User? = null

    protected abstract fun beginUnit()
    protected abstract fun endUnit()

    fun begin(userId: Int) {
        beginUnit()
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
        endUnit()
        user = null
    }

    suspend inline fun userAction(userId: Int, block: UserUnitOfWork.() -> Unit) {
        this.begin(userId)
        this.block()
        this.end()
    }

}
