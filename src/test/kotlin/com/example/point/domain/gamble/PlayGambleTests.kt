package com.example.point.domain.gamble

import com.example.point.domain.Constants
import com.example.point.domain.gamble.models.BettingGame
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

fun provideInvalidMultiplierWeightMap(): List<Arguments> {
    return listOf(
        Arguments.of(mutableMapOf(-1 to 1, 1 to 1, 2 to 2)),
        Arguments.of(mutableMapOf(-11 to 1, 2 to 2, 3 to 232)),
        Arguments.of(mutableMapOf(0 to -3, -155 to 1, 2 to 2)),
    )
}

fun provideMultiplierWeightMap(): List<Arguments> {
    return listOf(
        Arguments.of(mutableMapOf(0 to 1, 1 to 1, 2 to 1, 3 to 1)),
        Arguments.of(mutableMapOf(1 to 1, 2 to 200, 3 to 50)),
        Arguments.of(mutableMapOf(0 to 200, 1 to 10, 2 to 1)),
        Arguments.of((0..1000).associateBy({ it }, { 1 })),
        Arguments.of((0..1000).associateBy({ it }, { 1000 - it })),
    )
}

class PlayGambleTests {
    @ParameterizedTest
    @MethodSource("com.example.point.domain.gamble.PlayGambleTestsKt#provideInvalidMultiplierWeightMap")
    fun testInvalidGameInitialization(multiplerMap: MutableMap<Int, Int>) {
        assertFailsWith<IllegalArgumentException>(
            block = {
                BettingGame(multiplierProbWeights = multiplerMap)
            },
        )
    }

    @ParameterizedTest
    @MethodSource("com.example.point.domain.gamble.PlayGambleTestsKt#provideMultiplierWeightMap")
    fun testPlay(multiplierMap: MutableMap<Int, Int>) {
        val consumeCode = "consume-some"
        val rewardCode = "reward-some"
        val game =
            BettingGame(
                consumeProductCode = consumeCode,
                rewardProductCode = rewardCode,
                multiplierProbWeights = multiplierMap,
            )

        val hasZero = multiplierMap[0]?.let { it > 0 } ?: false

        val cosumeCodePrefix =
            Constants.GAMBLE_GAME_CONSUMPTION_CODE_FORMAT.substring(
                0,
                Constants.GAMBLE_GAME_CONSUMPTION_CODE_FORMAT.lastIndex - 1,
            )
        val rewardCodePrefix =
            Constants.GAMBLE_GAME_REWARD_CODE_FORMAT.substring(
                0,
                Constants.GAMBLE_GAME_REWARD_CODE_FORMAT.lastIndex - 1,
            )

        for (some in 0..30) {
            val betPoint = some + 1
            val (consumption, reward) = game.play(some + 1)

            assertEquals(betPoint, consumption.cost)
            assertEquals(consumeCode, consumption.productCode)
            assertTrue(
                consumption.code.startsWith(cosumeCodePrefix),
            )
            reward ?: assertTrue(hasZero)
            reward ?: continue

            assertEquals(rewardCode, reward.productCode)
            assertTrue(
                reward.code.startsWith(rewardCodePrefix),
            )
        }
    }
}
