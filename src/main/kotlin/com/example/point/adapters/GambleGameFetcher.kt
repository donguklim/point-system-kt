package com.example.point.adapters

import com.example.point.domain.gamble.models.BettingGame

interface GambleGameFetcher {
    suspend fun fetchBettingGame(): BettingGame?
}
