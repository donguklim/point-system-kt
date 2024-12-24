package com.example.point.api

import com.example.point.domain.commands.GetDailyChargeCommand
import com.example.point.domain.commands.PlayGameCommand
import com.example.point.domain.commands.PurchaseProductCommand
import com.example.point.domain.user.errors.PointError
import com.example.point.infrastructure.adapters.ExposedPointRepository
import com.example.point.infrastructure.adapters.RedisPointCache
import com.example.point.infrastructure.database.PointEvents
import com.example.point.service.MessageBus
import com.example.point.service.PointServiceError
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and

import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*


data class PointHistory(
    val numPoints: Int,
    val type: String,
    val title:String,
    val description: String,
    val transactionAt: String,
    val transactionCode: String
)

data class PointHistoriesResponse(
    val nextKey: Long,
    val histories: List<PointHistory>
)

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

    @GetMapping("/histories")
    suspend fun getHistories(
        @RequestParam(value = "size") size: Int = 20,
        @RequestParam(value = "key") pageKey: Long? = null,
    ): ResponseEntity<PointHistoriesResponse> {
        val userId = 1234L

        var nextKey: Long? = null
        val filteringExp = if (pageKey != null) (PointEvents.userId eq userId) and (PointEvents.id less pageKey) else PointEvents.userId eq userId
        val histories = newSuspendedTransaction(Dispatchers.IO) {
            PointEvents.select(
                PointEvents.id,
                PointEvents.numPoints,
                PointEvents.type,
                PointEvents.title,
                PointEvents.description,
                PointEvents.transactionAt,
                PointEvents.transactionCode
            ).where(filteringExp).orderBy(
                PointEvents.id to SortOrder.DESC,
            ).map {
                nextKey = it[PointEvents.id].value
                PointHistory(
                    it[PointEvents.numPoints],
                    it[PointEvents.type],
                    it[PointEvents.title],
                    it[PointEvents.description],
                    it[PointEvents.transactionAt].toString(),
                    it[PointEvents.transactionCode]
                )
            }
        }

        return ResponseEntity.ok(
            PointHistoriesResponse(nextKey=nextKey ?: 0, histories=histories)
        )
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