package com.example.point.infrastructure.uow

import com.example.point.application.uow.UserUnitOfWork
import com.example.point.domain.user.errors.TransactionError
import com.example.point.infrastructure.adapters.ExposedPointRepository
import com.example.point.infrastructure.adapters.RedisPointCache
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction


class RedisExposedUserUow(
    lockRedisHost: String,
    lockRedisPort: Int = 6379,
    repository: ExposedPointRepository,
    pointCache: RedisPointCache,
) : UserUnitOfWork(repository, pointCache) {
    private val lockManager = RedisUserLockManager(lockRedisHost, lockRedisPort)
    override suspend fun userUnit(userId: Long, unitLambda: suspend UserUnitOfWork.() -> Unit) {
        // lock by user ID in order to prevent
        // Duplicate consumptions of the same point charge
        val isLocked = lockManager.withLock(userId) {
            newSuspendedTransaction(Dispatchers.IO) {
                unitLambda()
            }
        }

        if (!isLocked) {
            throw TransactionError(
                userId = userId,
                reason = "Could not retrieve user lock for transaction",
            )
        }

    }
}