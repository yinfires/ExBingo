package me.jfenn.bingo.integrations.xaero

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player

object DummyXaeroMapApi : IXaeroMapApi {
    override fun isInstalled(): Boolean = false
    override fun registerTeamTracker(server: MinecraftServer, shouldTrack: (Player, Player) -> Boolean): Boolean = false
    override fun switchToFreshMapWorld(players: List<ServerPlayer>) {}
}
