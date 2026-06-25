package me.jfenn.bingo.common.timer

import me.jfenn.bingo.common.state.GameState
import kotlin.test.Test
import kotlin.test.assertEquals

class CountdownLockPolicyTest {
    @Test
    fun `only loading and countdown freeze ticks`() {
        val expected = mapOf(
            GameState.UNINITIALIZED to false,
            GameState.PREGAME to false,
            GameState.STARTING to false,
            GameState.PRELOADING to false,
            GameState.LOADING to true,
            GameState.COUNTDOWN to true,
            GameState.PLAYING to false,
            GameState.POSTGAME to false,
        )

        for ((state, shouldFreeze) in expected) {
            assertEquals(shouldFreeze, CountdownLockPolicy.shouldFreezeTicks(state), "freeze policy for $state")
        }
    }

    @Test
    fun `action prevention follows tick lock policy`() {
        for (state in GameState.entries) {
            assertEquals(
                CountdownLockPolicy.shouldFreezeTicks(state),
                CountdownLockPolicy.shouldPreventActions(state),
                "action prevention policy for $state",
            )
        }
    }
}
