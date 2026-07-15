package me.jfenn.bingo.mixinhandler

import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.platform.scope.BingoKoin
import net.minecraft.server.MinecraftServer

object MinecraftServerMixinHandler {

    fun shouldDeleteWorld(server: MinecraftServer): Boolean {
        val scope = BingoKoin.getScope(server) ?: return false
        val state = scope.get<BingoState>()
        return state.isLobbyMode
    }

    fun hasActiveGame(server: MinecraftServer): Boolean {
        val scope = BingoKoin.getScope(server) ?: return false
        val state = scope.get<BingoState>()
        return state.state.isActiveGame
    }

    @Deprecated("Use hasActiveGame; shutdown must protect STARTING/PRELOADING/LOADING/COUNTDOWN as well.")
    fun isGamePlaying(server: MinecraftServer): Boolean = hasActiveGame(server)

    fun isUnsafeSkipWorldClose(server: MinecraftServer): Boolean {
        val scope = BingoKoin.getScope(server) ?: return false
        val config = scope.get<BingoConfig>()
        return config.unsafeSkipWorldClose
    }

}
