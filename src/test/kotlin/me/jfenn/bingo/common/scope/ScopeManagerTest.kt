package me.jfenn.bingo.common.scope

import me.jfenn.bingo.common.options.BingoOptions
import me.jfenn.bingo.common.spawn.PlayerState
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScopeManagerTest {
    @Test
    fun `broken postgame reset save is detected`() {
        val state = BingoState(
            isLobbyMode = true,
            state = GameState.POSTGAME,
            options = BingoOptions(cards = emptyList()),
        )

        assertTrue(state.isBrokenPostgameResetSave())
    }

    @Test
    fun `postgame save with player data is not treated as broken`() {
        val state = BingoState(
            isLobbyMode = true,
            state = GameState.POSTGAME,
            options = BingoOptions(cards = emptyList()),
        )
        state.players[playerId] = PlayerState(lastState = GameState.POSTGAME)

        assertFalse(state.isBrokenPostgameResetSave())
    }

    private companion object {
        val playerId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001")
    }
}
