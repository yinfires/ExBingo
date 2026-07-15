package me.jfenn.bingo.common.game

import me.jfenn.bingo.common.state.GameState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GamePausePolicyTest {
    @Test
    fun `only pauses active games when no players are online`() {
        assertTrue(GamePausePolicy.shouldPauseForNoPlayers(GameState.PLAYING, onlinePlayerCount = 0))
        assertFalse(GamePausePolicy.shouldPauseForNoPlayers(GameState.PLAYING, onlinePlayerCount = 1))

        for (state in GameState.entries.filterNot { it == GameState.PLAYING }) {
            assertFalse(
                GamePausePolicy.shouldPauseForNoPlayers(state, onlinePlayerCount = 0),
                "pause policy for $state",
            )
        }
    }
}
