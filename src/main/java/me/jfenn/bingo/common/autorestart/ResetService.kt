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
    }

    fun resetGame() {
        if (!state.isLobbyMode) {
            error("This shouldn't be happening. Tell fennifith to stop writing bad code.")
        }

        try {
            tickManager.setFrozen(false)
            resetGameWorlds()
        } catch (e: Throwable) {
            log.error("[Reset] resetGameWorlds() failed", e)
            throw e
        } finally {
            tickManager.setFrozen(false)
        }
    }

    private fun resetGameWorlds() = log.measureTime("Resetting game...") {
        // Clear scoreboards (otherwise this causes an error when recreated)
        log.info("[Reset] Clearing game data")
        teamService.clearTeams()
        scoreboardService.clearScoreboards()
        bossBarService.clearBossBars()
        state.reset()

        if (dynamicWorldRecreateOnReset()) {
            log.warn("[Reset] dynamicWorldRecreateOnReset=true; using legacy live world recreation path")
            log.info("[Reset] Recreating worlds")
            tickManager.setFrozen(false)
            serverWorldFactory.recreateWorlds(Random.Default.nextLong()) {
                menuController.prepareLobbyFiles()
            }
            tickManager.setFrozen(false)
        } else {
            log.info("[Reset] Using NeoForge safe reset without live world recreation")
            tickManager.setFrozen(false)
        }

        log.info("[Reset] Teleporting players back to lobby")
        menuController.suspendPregameSpawn()
        state.changeState(eventBus, GameState.PREGAME) // invokes createInitialCards in ScoredItemCheck

        for (player in playerManager.getPlayers()) {
            playerController.restoreLobbyPlayerAfterReset(player)
        }

        log.info("[Reset] Transferring game state")
        // This is important! Otherwise the bingo persistent state does not get copied into the new world data
        // (ScopeManager does this on initial setup, but it gets lost when the world is replaced)
        persistentStateManager.put(
            type = persistentStates.bingo,
            value = state,
        )

        serverTaskExecutor.executeNextTick {
            tickManager.setFrozen(false)
            for (player in playerManager.getPlayers()) {
                player.resyncClientState(syncPosition = true)
            }

            serverTaskExecutor.executeNextTick {
                tickManager.setFrozen(false)
                menuController.spawnLobby()
                eventBus.emit(GameResetEvent, GameResetEvent())

                serverTaskExecutor.executeNextTick {
                    tickManager.setFrozen(false)
                    for (player in playerManager.getPlayers()) {
                        player.resyncClientState(syncPosition = true)
                    }
                }
            }
        }
    }

    private fun dynamicWorldRecreateOnReset(): Boolean {
        return java.lang.Boolean.getBoolean(DYNAMIC_WORLD_RECREATE_ON_RESET_PROPERTY)
    }

}
