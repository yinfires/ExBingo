package me.jfenn.bingo.common.timer

import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.spawn.SpawnService
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.team.TeamService
import me.jfenn.bingo.common.utils.minus
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
    private val spawnService: SpawnService,
    private val playerManager: IPlayerManager,
    private val log: Logger,
) {

    private var startedLoading: Instant? = null
    private var lastRecoveryTeleport: Instant? = null
    private var warnedStillWaiting = false

    init {
        events.onEnter(GameState.LOADING) {
            val now = Instant.now()
            startedLoading = now
            lastRecoveryTeleport = now
            warnedStillWaiting = false
            // Drop any movement / chunk-batch timestamps left over from the lobby or a previous
            // round before we start gating on them. Otherwise a stale ack recorded before this
            // LOADING phase could satisfy isPlayerLoaded() immediately, letting the client into
            // the world before its fresh spawn terrain arrives (void / can't break blocks).
            ServerPlayNetworkHandlerMixinHandler.markLoadingStarted(
                playerManager.getPlayers().map { it.player }
            )
            log.info("Waiting until all players have loaded spawn terrain...")
        }

        events.onGameTick {
            if (state.state != GameState.LOADING) return@onGameTick

            val startedLoading = startedLoading ?: return@onGameTick

            val unloadedPlayers = playerManager.getPlayers()
                .filter { teamService.isPlaying(it) }
                .filterNot { player ->
                    isPlayerLoaded(player, startedLoading) &&
                        spawnService.isPlayerSpawnChunkReady(player)
                }

            // Hold in LOADING until every playing client has confirmed its terrain arrived.
            // The client ack only proves block chunks arrived; the server-side player/entity
            // tracking for that chunk must also be live before PLAYING, otherwise players can
            // see terrain but have missing entities and block breaks that rubber-band back.
            // Starting anyway after a timeout recreates the exact failure this gate is meant to
            // prevent under memory pressure: the client is released into PLAYING while still
            // looking at void, can't move reliably, and other players see it as idle/invisible.
            // Instead, keep waiting and periodically re-send the spawn teleport to nudge chunk
            // tracking and client world sync; this is the automatic version of "quit and rejoin".
            if (unloadedPlayers.isNotEmpty()) {
                val now = Instant.now()
                val names = unloadedPlayers.joinToString { it.playerName }

                if (TerrainLoadingPolicy.shouldWarnStillWaiting(now, startedLoading, warnedStillWaiting)) {
                    log.warn("Still waiting for players to load spawn terrain after {}: {}", now - startedLoading, names)
                    warnedStillWaiting = true
                }

                if (TerrainLoadingPolicy.shouldSendRecoveryTeleport(now, startedLoading, lastRecoveryTeleport)) {
                    log.warn("Re-sending spawn teleport to players still loading terrain: {}", names)
                    lastRecoveryTeleport = now
                    unloadedPlayers.forEach { spawnService.forceTeleportPlayer(it) }
                }

                return@onGameTick
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
     * movement packet is kept as a fallback signal for that case only. On real network clients,
     * movement can arrive while the player still sees void, so it must not satisfy the gate.
     */
    private fun isPlayerLoaded(player: IPlayerHandle, defaultStartedLoading: Instant): Boolean {
        val startedLoading = ServerPlayNetworkHandlerMixinHandler.getLoadingStarted(player.player)
            ?: defaultStartedLoading
        val lastChunkBatch = ServerPlayNetworkHandlerMixinHandler.getLastChunkBatchReceived(player.player)
        val lastMovement = ServerPlayNetworkHandlerMixinHandler.getLastPlayerMovement(player.player)
        return TerrainLoadingPolicy.hasLoadedTerrain(
            lastChunkBatch = lastChunkBatch,
            lastMovement = lastMovement,
            loadingStarted = startedLoading,
            allowMovementFallback = player.player.connection.connection.isMemoryConnection,
        )
    }
}
