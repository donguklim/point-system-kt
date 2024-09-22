package com.example.point.domain

object DomainConstants {
    const val START_TIMESTAMP: Long = 1723302000 // 2024-08-11 in UTC

    // Not allowing the same point event within 1/TIME_RECORDING_SCALE second
    const val TIME_RECORDING_SCALE: Long = 10

    const val SCALED_START_TIMESTAMP: Long = START_TIMESTAMP * TIME_RECORDING_SCALE
    const val GAMBLE_GAME_CONSUMPTION_CODE_FORMAT = "betting:consume:%d"
    const val GAMBLE_GAME_REWARD_CODE_FORMAT = "betting:reward:%d"

    const val DEFAULT_GAME_BET_PRODUCT_CODE = "game_betting"
    const val DEFAULT_GAME_REWARD_PRODUCT_CODE = "game_reward"
}
