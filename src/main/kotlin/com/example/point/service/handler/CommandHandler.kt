package com.example.point.service.handler

import com.example.point.adapters.GambleGameFetcher
import com.example.point.adapters.ProductRepository
import com.example.point.service.UnitOfWork
import com.example.point.domain.Constants
import com.example.point.domain.commands.GetDailyChargeCommand
import com.example.point.domain.commands.PlayGameCommand
import com.example.point.domain.commands.PurchaseProductCommand
import com.example.point.domain.user.errors.InvalidProductError
import com.example.point.domain.valueObjects.ChargingPoints
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class CommandHandler{
    suspend fun getDailyCharge(command: GetDailyChargeCommand, uow: UnitOfWork) {
        val time_now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        uow.userAction(command.userId){ pointUser ->
            pointUser.chargePoints(
                ChargingPoints(
                    code = "daily:${time_now.year}-${time_now.monthNumber}-${time_now.dayOfMonth}",
                    numPoints = Constants.DAILY_POINT_BONUS,
                    title="daily bonus",
                    description = "daily bonus",
                )
            )
        }
    }

    suspend fun playGamble(
        command: PlayGameCommand,
        uow: UnitOfWork,
        gameFetcher: GambleGameFetcher
    ): Boolean {
        var ret = false
        uow.userAction(command.userId){ pointUser ->
            val bettingGame = gameFetcher.fetchBettingGame()
            val (consumption, reward) = bettingGame.play(command.betPoint)
            ret = pointUser.usePoints(consumption)
            if (ret) {
                reward?.let {
                    pointUser.chargePoints(it)
                }
            }
        }

        return ret
    }

    suspend fun buyProduct(
        command: PurchaseProductCommand,
        uow: UnitOfWork,
        productRepository: ProductRepository
    ) : Boolean{
        var ret = false
        uow.userAction(command.userId){ pointUser ->
            val consumption = productRepository.getConsumptionByProductCode(command.productCode)
                ?: throw InvalidProductError(command.userId, command.productCode)

            ret = pointUser.usePoints(
                consumption
            )
        }

        return ret
    }
}