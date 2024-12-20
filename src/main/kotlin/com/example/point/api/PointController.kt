package com.example.point.api

import com.example.point.domain.commands.GetDailyChargeCommand
import com.example.point.domain.commands.PlayGameCommand
import com.example.point.domain.commands.PurchaseProductCommand
import com.example.point.domain.user.errors.PointError
import com.example.point.infrastructure.adapters.ExposedPointRepository
import com.example.point.infrastructure.adapters.RedisPointCache
import com.example.point.service.MessageBus
import com.example.point.service.PointServiceError
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*


@RestController
@RequestMapping("/point")
class PointController(
    private val messageBus: MessageBus,
    private val pointCache: RedisPointCache,
    private val pointRepository: ExposedPointRepository,
) {
    data class PlayGameRequest( val point: Int )
    data class PurchaseRequest( val productCode: String)

    @GetMapping("/total-points")
    suspend fun getTotalPoints(): ResponseEntity<String> {
        val userId = 1234L
        var points = pointCache.getUserPoint(userId)
        if (points != null) return ResponseEntity.ok("$points")

        val expireAtThreshold = pointCache.getUserValidExpiryThreshold(userId)

        newSuspendedTransaction(Dispatchers.IO) {
            points = pointRepository.getPointSum(userId, expireAtThreshold)
        }

        return ResponseEntity.ok("$points")
    }
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