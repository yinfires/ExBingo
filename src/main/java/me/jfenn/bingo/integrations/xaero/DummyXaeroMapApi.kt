package me.jfenn.bingo.integrations.xaero

import net.minecraft.server.level.ServerPlayer

object DummyXaeroMapApi : IXaeroMapApi {
    override fun isInstalled(): Boolean = false
    override fun switchToFreshMapWorld(players: List<ServerPlayer>) {}
}
