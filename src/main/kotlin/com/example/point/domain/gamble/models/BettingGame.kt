package com.example.point.domain.gamble.models

import com.example.point.domain.DomainConstants
import com.example.point.domain.gamble.utils.RandomSelector
import com.example.point.domain.valueObjects.ChargingPoints
import com.example.point.domain.valueObjects.Consumption

class BettingGame(
    private val consumeProductCode: String = DomainConstants.DEFAULT_GAME_BET_PRODUCT_CODE,
    private val rewardProductCode: String = DomainConstants.DEFAULT_GAME_REWARD_PRODUCT_CODE,
    private val rewardTitle: String = "",
    private val rewardDesc: String = "",
    private val costDesc: String = "",
    multiplierProbWeights: MutableMap<Int, Int>,
) {
    private val selector: RandomSelector = RandomSelector(multiplierProbWeights)

    fun play(points: Int): Pair<Consumption, ChargingPoints?> {
        if (points <= 0) {
            throw IllegalArgumentException(
                "betting points must be greater than zero. but it is $points",
            )
        }

        val relativeTimestamp =
            System.currentTimeMillis() / (
                1000 / DomainConstants.TIME_RECORDING_SCALE
            ) - DomainConstants.SCALED_START_TIMESTAMP

        val consuming =
            Consumption(
                code =
                    String.format(
                        DomainConstants.GAMBLE_GAME_CONSUMPTION_CODE_FORMAT,
                        relativeTimestamp,
                    ),
                productCode = consumeProductCode,
                description = costDesc,
                cost = points,
            )

        val rewardPoints = selector.selectRandom() * points

        if (rewardPoints <= 0) {
            return Pair(consuming, null)
        }

        val charging =
            ChargingPoints(
                code =
                    String.format(
                        DomainConstants.GAMBLE_GAME_REWARD_CODE_FORMAT,
                        relativeTimestamp,
                    ),
                productCode = rewardProductCode,
                description = rewardDesc,
                title = rewardTitle,
                numPoints = rewardPoints,
            )

        return Pair(consuming, charging)
    }
}
