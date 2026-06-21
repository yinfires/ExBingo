package me.jfenn.bingo.common.commands

import me.jfenn.bingo.common.event.ScopedEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

class CommandTreeHandler(
    private val server: MinecraftServer,
    events: ScopedEvents,
) {

    private fun sendCommandTree(playerEntity: ServerPlayer) {
        server.playerList.sendPlayerPermissionLevel(playerEntity)
    }

    init {
        events.onStateChange {
            for (player in server.playerList.players.toList()) {
                sendCommandTree(player)
            }
        }

        events.onChangeTeam {
            sendCommandTree(it.player.player)
        }
    }

}
