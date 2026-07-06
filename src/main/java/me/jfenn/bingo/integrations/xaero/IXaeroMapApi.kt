package me.jfenn.bingo.integrations.xaero

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player

/**
 * Server-side integration with Xaero's Minimap / World Map.
 *
 * Used to give each bingo round its own Xaero "world id" (map cache namespace).
 * Returning to the lobby after a game switches every client to a fresh world id,
 * so the just-finished round's explored tiles and waypoints are no longer shown
 * — the only thing that hides them at runtime, since deleting the on-disk cache
 * does not affect Xaero's already-loaded in-memory map (and a return-to-lobby is
 * a dimension change, not a disconnect).
 */
interface IXaeroMapApi {
    fun isInstalled(): Boolean

    /**
     * Register an ExBingo team tracker with Xaero's synced player tracking system,
     * so teammates continue to appear on Xaero maps even when vanilla entity
     * tracking no longer sends them to each other's clients.
     */
    fun registerTeamTracker(server: MinecraftServer, shouldTrack: (Player, Player) -> Boolean): Boolean

    /**
     * Switch the given players' Xaero map to a fresh world id, isolating the
     * finished round's map/waypoints from what is shown next.
     */
    fun switchToFreshMapWorld(players: List<ServerPlayer>)
}
