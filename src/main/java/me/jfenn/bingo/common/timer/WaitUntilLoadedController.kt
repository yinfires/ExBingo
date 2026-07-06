package me.jfenn.bingo.common.timer

import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.team.TeamService
import me.jfenn.bingo.common.utils.milliseconds
import me.jfenn.bingo.common.utils.minus
import me.jfenn.bingo.common.utils.seconds
import me.jfenn.bingo.mixinhandler.ServerPlayNetworkHandlerMixinHandler
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.event.IEventBus
import org.slf4j.Logger
import java.time.Instant

/**
 * Wait until the player has finished loading terrain
 */
internal class WaitUntilLoadedController(
    events: ScopedEvents,
    eventBus: IEventBus,
    private val config: BingoConfig,
    private val state: BingoState,
    private val countdownService: CountdownService,
    private val teamService: TeamService,
    private val playerManager: IPlayerManager,
    private val log: Logger,
) {

    private var startedLoading: Instant? = null

    init {
        events.onEnter(GameState.LOADING) {
            startedLoading = Instant.now()
            // Drop any movement / chunk-batch timestamps left over from the lobby or a previous
            // round before we start gating on them. Otherwise a stale ack recorded before this
            // LOADING phase could satisfy isPlayerLoaded() immediately, letting the client into
            // the world before its fresh spawn terrain arrives (void / can't break blocks).
            ServerPlayNetworkHandlerMixinHandler.resetLoadingSignals(
                playerManager.getPlayers().map { it.player }
            )
            log.info("Waiting until all players have loaded spawn terrain...")
        }

        events.onGameTick {
            if (state.state != GameState.LOADING) return@onGameTick

            // +2 ticks to ensure that previous movement events are excluded
            val startedLoading = (startedLoading ?: return@onGameTick) + 40.milliseconds

            val isEveryPlayerLoaded = playerManager.getPlayers()
                .filter { teamService.isPlaying(it) }
                .all { player -> isPlayerLoaded(player, startedLoading) }

            // Hold in LOADING until every playing client has confirmed its terrain arrived.
            // A hard 40s timeout forces the game forward even if a client never reports, so a
            // single stuck connection can't hang the whole lobby. IMPORTANT: only advance once
            // loading is confirmed OR the timeout elapsed — the previous logic returned early
            // *when everyone was loaded* (condition inverted), so it never advanced on the
            // confirmed path and instead always fell through to the 40s timeout, dropping
            // high-latency clients into the world before terrain arrived (void / can't break
            // blocks / others see them idle).
            val timedOut = Instant.now() - startedLoading > 40.seconds
            if (!isEveryPlayerLoaded && !timedOut) return@onGameTick
            if (timedOut && !isEveryPlayerLoaded) {
                log.warn("Timed out waiting for all players to load spawn terrain; starting anyway.")
            }
            log.info("Waiting until all players have loaded spawn terrain... Done! (${(Instant.now() - startedLoading)})")

            // change to the playing state
            if (config.countdownDelayTicks > 0 || config.countdownSeconds > 0) {
                state.changeState(eventBus, GameState.COUNTDOWN)
            } else {
                state.changeState(eventBus, GameState.PLAYING)
                countdownService.sendCountdownPacket(CountdownPacket(0))
            }
        }
    }

    /**
     * A player is considered to have loaded their spawn terrain once, after being teleported
     * into the spawn dimension (i.e. after [startedLoading]), their client has acknowledged
     * receiving a batch of chunks. This is a direct signal that terrain arrived on the client.
     *
     * On a single-player integrated server the host uses an in-memory connection where chunks
     * are delivered synchronously and a chunk-batch ack may not follow the same timing, so the
     * movement packet is kept as a fallback signal for that case.
     */
    private fun isPlayerLoaded(player: IPlayerHandle, startedLoading: Instant): Boolean {
        val lastChunkBatch = ServerPlayNetworkHandlerMixinHandler.getLastChunkBatchReceived(player.player)
        if (lastChunkBatch != null && lastChunkBatch > startedLoading) return true

        val lastMovement = ServerPlayNetworkHandlerMixinHandler.getLastPlayerMovement(player.player)
        return lastMovement != null && lastMovement > startedLoading
    }
}