package me.jfenn.bingo.common.state

import me.jfenn.bingo.platform.IPersistentStateType

internal interface ResetPersistentStates {
    val bingo: IPersistentStateType<BingoState>
}
