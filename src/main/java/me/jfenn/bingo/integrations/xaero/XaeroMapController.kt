package me.jfenn.bingo.integrations.xaero

import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.event.ICallbackHandle
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.game.GameResetEvent
import me.jfenn.bingo.platform.event.model.TickEvent
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player

/**
 * On returning to the lobby after a game ([GameResetEvent]), switch every online
 * player's Xaero map to a fresh world id so the finished round's explored tiles
 * and waypoints stop showing. By this point the reset has already teleported
 * players back to the lobby dimension, so Xaero has settled on the lobby and our
 * fresh id wins.
 */
class XaeroMapController(
    eventBus: IEventBus,
    private val server: MinecraftServer,
    private val playerManager: IPlayerManager,
    private val state: BingoState,
    private val xaeroMap: IXaeroMapApi,
) : BingoComponent() {
    private var teamTrackerRetryHandle: ICallbackHandle? = null

    init {
        if (xaeroMap.isInstalled()) {
            registerTeamTrackerOrRetry(eventBus)
        }

        eventBus.register(GameResetEvent) {
            if (xaeroMap.isInstalled()) {
                xaeroMap.switchToFreshMapWorld(playerManager.getPlayers().map { it.player })
            }
        }
    }

    private fun registerTeamTrackerOrRetry(eventBus: IEventBus) {
        if (registerTeamTracker()) return

        teamTrackerRetryHandle = eventBus.register(TickEvent.Start) {
            if (registerTeamTracker()) {
                teamTrackerRetryHandle?.close()
                teamTrackerRetryHandle = null
            }
        }
    }

    private fun registerTeamTracker(): Boolean =
        xaeroMap.registerTeamTracker(server, ::shouldTrackAsTeammate)

    private fun shouldTrackAsTeammate(viewer: Player, tracked: Player): Boolean {
        if (!state.state.isPlayingOrCountdown) return false

        val viewerPlayer = viewer as? ServerPlayer ?: return false
        val trackedPlayer = tracked as? ServerPlayer ?: return false
        return state.teams.values.any { team ->
            team.isPlaying() && team.includesPlayer(viewerPlayer) && team.includesPlayer(trackedPlayer)
        }
    }
}
