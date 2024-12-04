package com.example.point.infrastructure.adapters

import com.example.point.adapters.GambleGameFetcher
import com.example.point.domain.gamble.models.BettingGame

class ConstantGambleGameFetcher: GambleGameFetcher {
    override suspend fun fetchBettingGame(): BettingGame {
        return BettingGame(
            rewardTitle = "Bet Reward",
            costTitle = "Bet Points",
            costDesc = "Points used for betting game",
            rewardDesc = "Reward Points from betting game",
            multiplierProbWeights = mapOf(0 to 1, 1 to 1, 2 to 1, 10 to 1, 100 to 1)
        )
    }
}