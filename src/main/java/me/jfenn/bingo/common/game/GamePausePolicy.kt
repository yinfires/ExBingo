package me.jfenn.bingo.common.game

import me.jfenn.bingo.common.state.GameState

internal object GamePausePolicy {
    fun shouldPauseForNoPlayers(
        state: GameState,
        onlinePlayerCount: Int,
    ): Boolean {
        return state == GameState.PLAYING && onlinePlayerCount == 0
    }
}
