package com.example.point.infrastructure.uow

import com.example.point.application.uow.UserUnitOfWork
import com.example.point.infrastructure.adapters.ExposedPointRepository
import com.example.point.infrastructure.adapters.RedisPointCache
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction


class RedisExposedUserUow(
    repository: ExposedPointRepository,
    pointCache: RedisPointCache,
) : UserUnitOfWork(repository, pointCache) {

    override suspend fun userUnit(unitLambda: suspend UserUnitOfWork.() -> Unit) {
        newSuspendedTransaction(Dispatchers.IO) {
            unitLambda()
        }
    }
}