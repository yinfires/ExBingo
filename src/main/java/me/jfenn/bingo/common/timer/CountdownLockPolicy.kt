package me.jfenn.bingo.common.timer

import me.jfenn.bingo.common.state.GameState

internal object CountdownLockPolicy {
    fun shouldFreezeTicks(state: GameState): Boolean {
        return state == GameState.LOADING || state == GameState.COUNTDOWN
    }

    fun shouldPreventActions(state: GameState): Boolean {
        return shouldFreezeTicks(state)
    }
}
