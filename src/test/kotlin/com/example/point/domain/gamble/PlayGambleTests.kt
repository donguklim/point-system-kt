package com.example.point.domain.gamble

import kotlin.test.*

import com.example.point.domain.gamble.models.BettingGame
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

fun provideInvalidMultiplierWeightMap(): List<Arguments> {
    return listOf(
        Arguments.of(mutableMapOf(-1 to 1, 1 to 1, 2 to 2)),
        Arguments.of(mutableMapOf(-11 to 1, 2 to 2, 3 to 232)),
        Arguments.of(mutableMapOf(0 to -3, -155 to 1, 2 to 2)),
    )
}


class PlayGambleTests {

    @ParameterizedTest
    @MethodSource("com.example.point.domain.gamble.PlayGambleTestsKt#provideInvalidMultiplierWeightMap")
    fun testInvalidGameInitialization(multiplerMap: MutableMap<Int, Int> ) {

        assertFailsWith<IllegalArgumentException>(
            block = {
                BettingGame(multiplierProbWeights = multiplerMap)
            }
        )

    }
}