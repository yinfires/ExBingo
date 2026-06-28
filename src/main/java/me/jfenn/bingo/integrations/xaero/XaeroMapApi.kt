package me.jfenn.bingo.integrations.xaero

import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import xaero.common.HudMod
import xaero.common.server.level.LevelMapProperties

/**
 * Real Xaero integration. Sends a [LevelMapProperties] (Xaero packet id 0 on the
 * `xaerominimap:main` channel) carrying a fresh world id to each player, which
 * makes their Xaero minimap/world-map switch to a new, empty cache namespace —
 * hiding the finished round's explored tiles and waypoints.
 *
 * [LevelMapProperties] has no public id setter; its no-arg constructor assigns a
 * fresh random id (and sets usable=true), which is exactly what we want: one new
 * instance per round, sent identically to every player so they share the round's
 * namespace.
 *
 * Sending reuses Xaero's already-registered channel via
 * `HudMod.getMessageHandler().sendToPlayer(...)`; we must NOT register the
 * channel ourselves (Xaero owns it and a duplicate registration would conflict).
 */
class XaeroMapApi : IXaeroMapApi {
    private val log = LoggerFactory.getLogger(XaeroMapApi::class.java)

    override fun isInstalled(): Boolean = true

    override fun switchToFreshMapWorld(players: List<ServerPlayer>) {
        if (players.isEmpty()) return

        val hudMod = HudMod.INSTANCE
        if (hudMod == null) {
            log.warn("[XaeroMapApi] HudMod.INSTANCE is null; cannot switch map world")
            return
        }
        val messageHandler = hudMod.messageHandler ?: run {
            log.warn("[XaeroMapApi] Xaero message handler unavailable; cannot switch map world")
            return
        }

        // One fresh id (random per construction) shared by all players this round.
        val properties = LevelMapProperties()
        var sent = 0
        for (player in players) {
            try {
                messageHandler.sendToPlayer(player, properties)
                sent++
            } catch (e: Throwable) {
                log.warn("[XaeroMapApi] Failed to send map world id to {}", player.scoreboardName, e)
            }
        }
        log.info("[XaeroMapApi] Switched {} player(s) to fresh Xaero map world id {}", sent, properties.id)
    }
}
