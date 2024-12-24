package com.example.point.service

import com.example.point.adapters.GambleGameFetcher
import com.example.point.adapters.ProductRepository
import com.example.point.domain.commands.GetDailyChargeCommand
import com.example.point.domain.commands.PlayGameCommand
import com.example.point.domain.commands.PurchaseProductCommand
import com.example.point.domain.commands.UserCommand
import com.example.point.domain.events.NotEnoughPointEvent
import com.example.point.domain.events.PointChangeEvent
import com.example.point.domain.events.UserEvent
import com.example.point.service.handler.CommandHandler
import com.example.point.service.handler.EventHandler

class MessageBus(
    private val uow : UnitOfWork,
    private val gameFetcher: GambleGameFetcher,
    private val productRepository: ProductRepository
) {
    private val commandHandler = CommandHandler()
    private val eventHandler = EventHandler()

    suspend fun handleEvent(event: UserEvent) {
        when (event) {
            is NotEnoughPointEvent -> eventHandler.resetTotalPoints(event, uow.pointCache)
            is PointChangeEvent -> eventHandler.addUserPoints(event, uow.pointCache)
            else -> throw InvalidEventError(event)
        }
    }

    suspend fun handleCommand(command: UserCommand): Boolean {

        var isPointEnough = true
        when (command) {
            is GetDailyChargeCommand -> commandHandler.getDailyCharge(command, uow)
            is PlayGameCommand -> isPointEnough = commandHandler.playGamble(command, uow, gameFetcher)
            is PurchaseProductCommand -> isPointEnough = commandHandler.buyProduct(command, uow, productRepository)
            else -> throw InvalidCommandError(command)

        }

        uow.user?.collectEvents()?.forEach { event ->
            handleEvent(event)
        }

        return isPointEnough

    }
}
