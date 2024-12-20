package com.example.point.api

import com.example.point.domain.commands.GetDailyChargeCommand
import com.example.point.domain.commands.PlayGameCommand
import com.example.point.domain.commands.PurchaseProductCommand
import com.example.point.domain.user.errors.PointError
import com.example.point.service.MessageBus
import com.example.point.service.PointServiceError
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


data class PlayGameRequest( val point: Int )
data class PurchaseRequest( val productCode: String)

@RestController
@RequestMapping("/point")
class PointController(
    private val messageBus: MessageBus
) {
    @PostMapping("/daily-point")
    suspend fun dailyPoint(): ResponseEntity<String> {
        val userId = 1234L
        val res: Boolean

        try {
            res = messageBus.handleCommand(
                GetDailyChargeCommand(userId)
            )
        } catch (error: PointError) {
            return ResponseEntity.badRequest().body(error.message)
        } catch (error: PointServiceError) {
            return ResponseEntity.internalServerError().body(error.message)
        }

        if (!res) return ResponseEntity.badRequest().body("Not enough points")

        return ResponseEntity.ok().body("Received daily charge")
    }

    @PostMapping("/play-game")
    suspend fun playGame(@RequestBody bet: PlayGameRequest): ResponseEntity<String> {
        val userId = 1234L
        val res: Boolean

        try {
            res = messageBus.handleCommand(
                PlayGameCommand(userId, bet.point)
            )
        } catch (error: PointError) {
            return ResponseEntity.badRequest().body(error.message)
        } catch (error: PointServiceError) {
            return ResponseEntity.internalServerError().body(error.message)
        }

        if (!res) return ResponseEntity.badRequest().body("Not enough points")

        return ResponseEntity.ok().body("played game")
    }

    @PostMapping("/purchase")
    suspend fun purchase(@RequestBody purchasing: PurchaseRequest): ResponseEntity<String> {
        val userId = 1234L
        val res: Boolean

        try {
            res = messageBus.handleCommand(
                PurchaseProductCommand(userId, purchasing.productCode)
            )
        } catch (error: PointError) {
            return ResponseEntity.badRequest().body(error.message)
        } catch (error: PointServiceError) {
            return ResponseEntity.internalServerError().body(error.message)
        }

        if (!res) return ResponseEntity.badRequest().body("Not enough points")

        return ResponseEntity.ok().body("purchase complete")
    }
}