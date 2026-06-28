package me.jfenn.bingo.common.autorestart

import me.jfenn.bingo.common.bossbar.ResetBossBarService
import me.jfenn.bingo.common.menu.RuntimeLobbyController
import me.jfenn.bingo.common.scoreboard.ResetScoreboardService
import me.jfenn.bingo.common.spawn.LobbyPlayerRestorer
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.state.ResetPersistentStates
import me.jfenn.bingo.common.team.ResetTeamService
import me.jfenn.bingo.common.utils.measureTime
import me.jfenn.bingo.platform.IPersistentStateManager
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.IExecutors
import me.jfenn.bingo.platform.IServerWorldFactory
import me.jfenn.bingo.platform.ITickManager
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.game.GameResetEvent
import org.slf4j.Logger
import kotlin.random.Random

internal class ResetService(
    private val eventBus: IEventBus,
    private val state: BingoState,
    private val playerManager: IPlayerManager,
    private val playerController: LobbyPlayerRestorer,
    private val teamService: ResetTeamService,
    private val scoreboardService: ResetScoreboardService,
    private val bossBarService: ResetBossBarService,
    private val serverWorldFactory: IServerWorldFactory,
    private val menuController: RuntimeLobbyController,
    private val persistentStateManager: IPersistentStateManager,
    private val persistentStates: ResetPersistentStates,
    private val tickManager: ITickManager,
    private val serverTaskExecutor: IExecutors.IServerTaskExecutor,
    private val log: Logger,
) {
    private companion object {
        const val DYNAMIC_WORLD_RECREATE_ON_RESET_PROPERTY = "exbingo.dynamicWorldRecreateOnReset"

        // Cap on how long we wait for players' entity sections to become
        // accessible before forcing the tracking refresh anyway. Mirrors
        // MenuController.MAX_ENTITY_READY_WAIT_TICKS (~5s at 20 tps).
        const val MAX_ENTITY_TRACKING_WAIT_TICKS = 100
    }

    fun resetGame() {
        if (!state.isLobbyMode) {
            error("This shouldn't be happening. Tell fennifith to stop writing bad code.")
        }

        logResetState("before resetGame")
        try {
            tickManager.setFrozen(false)
            logResetState("after initial unfreeze")
            resetGameWorlds()
        } catch (e: Throwable) {
            log.error("[Reset] resetGameWorlds() failed", e)
            throw e
        } finally {
            tickManager.setFrozen(false)
            logResetState("after resetGame finally")
        }
    }

    private fun resetGameWorlds() = log.measureTime("Resetting game...") {
        // Clear scoreboards (otherwise this causes an error when recreated)
        log.info("[Reset] Clearing game data")
        teamService.clearTeams()
        scoreboardService.clearScoreboards()
        bossBarService.clearBossBars()
        state.reset()
        logResetState("after state.reset")

        if (dynamicWorldRecreateOnReset()) {
            log.warn("[Reset] dynamicWorldRecreateOnReset=true; using legacy live world recreation path")
            log.info("[Reset] Recreating worlds")
            tickManager.setFrozen(false)
            serverWorldFactory.recreateWorlds(Random.Default.nextLong()) {
                menuController.prepareLobbyFiles()
            }
            tickManager.setFrozen(false)
            logResetState("after recreateWorlds")
        } else {
            log.info("[Reset] Using NeoForge safe reset without live world recreation")
            tickManager.setFrozen(false)
            logResetState("after safe reset world retention")
        }

        log.info("[Reset] Transferring game state")
        // This is important! Otherwise the bingo persistent state does not get copied into the new world data
        // (ScopeManager does this on initial setup, but it gets lost when the world is replaced)
        persistentStateManager.put(
            type = persistentStates.bingo,
            value = state,
        )

        log.info("[Reset] Teleporting players back to lobby")
        menuController.suspendPregameSpawn()
        state.changeState(eventBus, GameState.PREGAME) // invokes createInitialCards in ScoredItemCheck
        logResetState("after PREGAME transition")

        for (player in playerManager.getPlayers()) {
            playerController.restoreLobbyPlayerAfterReset(player)
        }

        tickManager.setFrozen(false)
        logResetState("after restoring lobby players")

        serverTaskExecutor.executeNextTick {
            tickManager.setFrozen(false)
            logResetState("first next tick before resync")
            for (player in playerManager.getPlayers()) {
                player.resyncClientState(syncPosition = true)
            }

            serverTaskExecutor.executeNextTick {
                tickManager.setFrozen(false)
                menuController.spawnLobby()
                eventBus.emit(GameResetEvent, GameResetEvent())
                logResetState("second next tick after spawnLobby")

                serverTaskExecutor.executeNextTick {
                    tickManager.setFrozen(false)
                    for (player in playerManager.getPlayers()) {
                        player.resyncClientState(syncPosition = true)
                    }
                    // Rebuild entity-tracker pairings so all clients reliably see
                    // each other again (fixes the post-reset "players invisible
                    // until they move close" bug). This MUST wait until each
                    // player's own entity section is accessible: the lobby
                    // teleport registers the player via addNewEntityWithoutEvent,
                    // but startTracking() (which puts the player into
                    // ChunkMap.entityMap) only fires once their section is
                    // accessible, and NeoForge promotes entity sections
                    // asynchronously a few ticks after the chunk loads. Calling
                    // refreshEntityTracking() before then would iterate an
                    // entityMap that doesn't yet contain the players and pair
                    // nothing.
                    awaitEntityTrackingReadyThenRefresh(waitedTicks = 0)
                    logResetState("third next tick after lobby resync")
                }
            }
        }
    }

    private fun dynamicWorldRecreateOnReset(): Boolean {
        return java.lang.Boolean.getBoolean(DYNAMIC_WORLD_RECREATE_ON_RESET_PROPERTY)
    }

    /**
     * Wait until every player is tracking-ready — registered in the entity tracker
     * AND their own chunk delivered to the client — before forcing a re-pair.
     * Re-pairing earlier sends a spawn packet the client discards (its chunk isn't
     * loaded yet), which is the real cause of the post-reset invisibility. Retries
     * each tick until all players are ready or [MAX_ENTITY_TRACKING_WAIT_TICKS]
     * elapses, then refreshes regardless.
     *
     * See the call site in [resetGameWorlds] for why the wait is required.
     */
    private fun awaitEntityTrackingReadyThenRefresh(waitedTicks: Int) {
        val players = playerManager.getPlayers()
        val ready = players.all { it.isEntityTrackingReady() }

        if (!ready && waitedTicks < MAX_ENTITY_TRACKING_WAIT_TICKS) {
            serverTaskExecutor.executeNextTick {
                awaitEntityTrackingReadyThenRefresh(waitedTicks + 1)
            }
            return
        }

        if (!ready) {
            log.error(
                "[Reset] Players not tracking-ready after {} ticks; refreshing tracking anyway (players may stay invisible until they move)",
                waitedTicks,
            )
        } else if (waitedTicks > 0) {
            log.info("[Reset] Players tracking-ready after {} tick(s)", waitedTicks)
        }

        for (player in players) {
            player.refreshEntityTracking()
        }

        // Diagnostic: report how many other players can now see each player. If
        // these counts are < (players-1) the pairing didn't take and the
        // invisibility bug will reproduce — capture this before blaming movement.
        if (players.size > 1) {
            val chunkMap = players.first().serverWorld.chunkSource.chunkMap
            val visibility = players.joinToString { player ->
                val viewers = runCatching { chunkMap.getPlayersWatching(player.player).size }
                    .getOrDefault(-1)
                "${player.playerName}=$viewers"
            }
            log.info(
                "[Reset] Entity tracking refreshed for {} players; viewers-per-player (expect {}): {}",
                players.size,
                players.size - 1,
                visibility,
            )
        }
    }

    private fun logResetState(stage: String) {
        var hasDetachedPlayer = false
        val playerStates = playerManager.getPlayers().joinToString { player ->
            val world = player.serverWorld
            val currentWorld = serverWorldFactory.listWorlds()
                .firstOrNull { it.world.dimension() == world.dimension() }
                ?.world
            val attached = currentWorld === world
            hasDetachedPlayer = hasDetachedPlayer || !attached
            val currentWorldId = currentWorld?.let { System.identityHashCode(it).toString() } ?: "null"
            "${player.playerName}@${world.dimension().location()}#${System.identityHashCode(world)}:current=$currentWorldId:attached=$attached:${player.gameMode}:spectator=${player.isSpectator}:alive=${player.isAlive}"
        }
        val message = "[ResetDiag] {}: state={}, gameId={}, frozen={}, runsNormally={}, menuEntities={}, players={}"
        val menuStats = menuController.menuEntityStats()
        val shouldError = (state.state == GameState.PREGAME || state.state == GameState.PLAYING) &&
            (tickManager.isFrozen || hasDetachedPlayer)
        if (shouldError) {
            log.error(message, stage, state.state, state.gameId, tickManager.isFrozen, tickManager.runsNormally, menuStats, playerStates)
        } else {
            log.info(message, stage, state.state, state.gameId, tickManager.isFrozen, tickManager.runsNormally, menuStats, playerStates)
        }
    }

}
