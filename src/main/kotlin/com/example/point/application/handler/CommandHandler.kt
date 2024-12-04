package com.example.point.application.handler

import com.example.point.adapters.GambleGameFetcher
import com.example.point.adapters.ProductRepository
import com.example.point.application.uow.UserUnitOfWork
import com.example.point.domain.Constants
import com.example.point.domain.gamble.models.BettingGame
import com.example.point.domain.valueObjects.ChargingPoints
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class CommandHandler {
    suspend fun getDailyCharge(userId: Long, uow: UserUnitOfWork) {
        val time_now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        uow.userAction(userId){ pointUser ->
            pointUser.chargePoints(
                ChargingPoints(
                    code = "daily:${time_now.year}-${time_now.month}-${time_now.dayOfMonth}",
                    numPoints = Constants.DAILY_POINT_BONUS,
                    title="daily bonus",
                    description = "daily bonus",
                )
            )
        }
    }

    suspend fun playGamble(
        betPoint: Int,
        userId: Long,
        uow: UserUnitOfWork,
        gameFetcher: GambleGameFetcher
    ): Boolean {
        var ret = false
        uow.userAction(userId){ pointUser ->
            val bettingGame = gameFetcher.fetchBettingGame()
            val (consumption, bonus) = bettingGame.play(betPoint)
            if (pointUser.usePoints(consumption)) {
                bonus?.let {
                    pointUser.chargePoints(it)
                }
                ret = true
            }
        }

        return ret
    }

    suspend fun buyProduct(
        productCode: String,
        userId: Long,
        uow: UserUnitOfWork,
        productRepository: ProductRepository
    ) : Boolean{
        var ret = false
        uow.userAction(userId){ pointUser ->
            val consumption = productRepository.getConsumptionByProductCode(productCode)
            consumption?.let {
                ret = pointUser.usePoints(
                    consumption
                )
            }
        }

        return ret
    }
}