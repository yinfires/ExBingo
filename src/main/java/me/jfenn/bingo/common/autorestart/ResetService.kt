package me.jfenn.bingo.common.autorestart

import me.jfenn.bingo.common.bossbar.BossBarService
import me.jfenn.bingo.common.menu.MenuController
import me.jfenn.bingo.common.scoreboard.ScoreboardService
import me.jfenn.bingo.common.spawn.PlayerController
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.state.PersistentStates
import me.jfenn.bingo.common.team.TeamService
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
    private val playerController: PlayerController,
    private val teamService: TeamService,
    private val scoreboardService: ScoreboardService,
    private val bossBarService: BossBarService,
    private val serverWorldFactory: IServerWorldFactory,
    private val menuController: MenuController,
    private val persistentStateManager: IPersistentStateManager,
    private val persistentStates: PersistentStates,
    private val tickManager: ITickManager,
    private val serverTaskExecutor: IExecutors.IServerTaskExecutor,
    private val log: Logger,
) {

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

    private fun resetGameWorlds() = log.measureTime("Resetting worlds...") {
        // Clear scoreboards (otherwise this causes an error when recreated)
        log.info("[Reset] Clearing game data")
        teamService.clearTeams()
        scoreboardService.clearScoreboards()
        bossBarService.clearBossBars()
        state.reset()

        log.info("[Reset] Recreating worlds")
        tickManager.setFrozen(false)
        serverWorldFactory.recreateWorlds(Random.Default.nextLong()) {
            menuController.prepareLobbyFiles()
        }
        tickManager.setFrozen(false)

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

        for (player in playerManager.getPlayers()) {
            playerController.restoreLobbyPlayerAfterReset(player)
        }

        tickManager.setFrozen(false)

        serverTaskExecutor.executeNextTick {
            tickManager.setFrozen(false)
            for (player in playerManager.getPlayers()) {
                player.resyncClientState()
            }

            serverTaskExecutor.executeNextTick {
                tickManager.setFrozen(false)
                menuController.spawnLobby()
                eventBus.emit(GameResetEvent, GameResetEvent())
                log.debug("[Reset] post-reset state: frozen={}, runsNormally={}, menuEntities={}", tickManager.isFrozen, tickManager.runsNormally, menuController.menuEntityCount())
            }
        }
    }

}
