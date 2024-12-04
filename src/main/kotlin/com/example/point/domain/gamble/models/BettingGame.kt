package com.example.point.domain.gamble.models

import com.example.point.domain.Constants
import com.example.point.domain.gamble.utils.RandomSelector
import com.example.point.domain.user.models.Consumption
import com.example.point.domain.valueObjects.ChargingPoints

class BettingGame(
    private val rewardTitle: String = "",
    private val rewardDesc: String = "",
    private val costTitle: String = "",
    private val costDesc: String = "",
    multiplierProbWeights: Map<Int, Int>,
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
                1000 / Constants.TIME_RECORDING_SCALE
            ) - Constants.SCALED_START_TIMESTAMP

        val consuming =
            Consumption(
                code =
                    String.format(
                        Constants.GAMBLE_GAME_CONSUMPTION_CODE_FORMAT,
                        relativeTimestamp,
                    ),
                description = costDesc,
                title = costTitle,
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
                        Constants.GAMBLE_GAME_REWARD_CODE_FORMAT,
                        relativeTimestamp,
                    ),
                description = rewardDesc,
                title = rewardTitle,
                numPoints = rewardPoints,
            )

        return Pair(consuming, charging)
    }
}
