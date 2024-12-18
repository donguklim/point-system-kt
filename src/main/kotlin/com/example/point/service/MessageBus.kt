package com.example.point.service

import com.example.point.adapters.GambleGameFetcher
import com.example.point.adapters.ProductRepository
import com.example.point.domain.commands.GetDailyChargeCommand
import com.example.point.domain.commands.PlayGameCommand
import com.example.point.domain.commands.PurchaseProductCommand
import com.example.point.domain.commands.UserCommand
import com.example.point.domain.events.NotEnoughPointEvent
import com.example.point.domain.events.UserEvent
import com.example.point.infrastructure.adapters.RedisPointCache
import com.example.point.service.handler.CommandHandler
import com.example.point.service.handler.EventHandler

class MessageBus(
    private val uow : UnitOfWork,
    private val pointCache: RedisPointCache,
    private val gameFetcher: GambleGameFetcher,
    private val productRepository: ProductRepository
) {
    private val commandHandler = CommandHandler()
    private val eventHandler = EventHandler()

    suspend fun handleEvent(event: UserEvent) {
        when (event) {
            is NotEnoughPointEvent -> eventHandler.resetTotalPoint(event, pointCache)
        }
    }

    suspend fun handleCommand(command: UserCommand) {
        when (command) {
            is GetDailyChargeCommand -> commandHandler.getDailyCharge(command, uow)
            is PlayGameCommand -> commandHandler.playGamble(command, uow, gameFetcher)
            is PurchaseProductCommand -> commandHandler.buyProduct(command, uow, productRepository)
        }

        uow.user?.collectEvents()?.forEach { event ->
            handleEvent(event)
        }

    }
}
