package me.jfenn.bingo.common.spawn

import me.jfenn.bingo.platform.IPlayerHandle

internal interface LobbyPlayerRestorer {
    fun restoreLobbyPlayerAfterReset(player: IPlayerHandle)
}
