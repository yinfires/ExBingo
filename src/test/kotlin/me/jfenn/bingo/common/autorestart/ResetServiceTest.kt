package me.jfenn.bingo.common.autorestart

import me.jfenn.bingo.common.bossbar.ResetBossBarService
import me.jfenn.bingo.common.menu.MenuEntityStats
import me.jfenn.bingo.common.menu.RuntimeLobbyController
import me.jfenn.bingo.common.options.BingoOptions
import me.jfenn.bingo.common.scoreboard.ResetScoreboardService
import me.jfenn.bingo.common.spawn.LobbyPlayerRestorer
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.state.ResetPersistentStates
import me.jfenn.bingo.common.team.ResetTeamService
import me.jfenn.bingo.platform.IExecutors
import me.jfenn.bingo.platform.IPersistentStateManager
import me.jfenn.bingo.platform.IPersistentStateType
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.IServerWorld
import me.jfenn.bingo.platform.IServerWorldFactory
import me.jfenn.bingo.platform.ITickManager
import me.jfenn.bingo.platform.event.ICallbackHandle
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.IReturnEvent
import me.jfenn.bingo.platform.event.game.GameResetEvent
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.Executor
import java.util.function.Function
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResetServiceTest {
    @Test
    fun `safe reset defaults to state reset lobby restore and skips live world recreation`() {
        val calls = mutableListOf<String>()
        val eventBus = RecordingEventBus(calls)
        val state = BingoState(
            isLobbyMode = true,
            state = GameState.POSTGAME,
            options = BingoOptions(cards = emptyList()),
        )
        val playerManager = EmptyPlayerManager
        val playerController = RecordingLobbyPlayerRestorer(calls)
        val teamService = RecordingResetTeamService(calls)
        val scoreboardService = RecordingResetScoreboardService(calls)
        val bossBarService = RecordingResetBossBarService(calls)
        val serverWorldFactory = RecordingServerWorldFactory(calls)
        val menuController = RecordingMenuController(calls)
        val persistentStateManager = RecordingPersistentStateManager(calls)
        val persistentStates = RecordingPersistentStates()
        val tickManager = RecordingTickManager(calls)
        val serverTaskExecutor = ImmediateServerTaskExecutor(calls)
        val log = LoggerFactory.getLogger("ResetServiceTest")

        val service = ResetService(
            eventBus = eventBus,
            state = state,
            playerManager = playerManager,
            playerController = playerController,
            teamService = teamService,
            scoreboardService = scoreboardService,
            bossBarService = bossBarService,
            serverWorldFactory = serverWorldFactory,
            menuController = menuController,
            persistentStateManager = persistentStateManager,
            persistentStates = persistentStates,
            tickManager = tickManager,
            serverTaskExecutor = serverTaskExecutor,
            log = log,
        )

        val propertyName = "exbingo.dynamicWorldRecreateOnReset"
        val oldProperty = System.getProperty(propertyName)
        try {
            System.clearProperty(propertyName)
            service.resetGame()
        } finally {
            if (oldProperty == null) {
                System.clearProperty(propertyName)
            } else {
                System.setProperty(propertyName, oldProperty)
            }
        }

        assertFalse(serverWorldFactory.recreateCalled)
        assertEquals(1, teamService.clearCalls)
        assertEquals(1, scoreboardService.clearCalls)
        assertEquals(1, bossBarService.clearCalls)
        assertEquals(1, menuController.suspendCalls)
        assertEquals(1, menuController.spawnCalls)
        assertEquals(0, menuController.prepareCalls)
        assertEquals(1, persistentStateManager.putCalls)
        assertEquals(GameState.PREGAME, persistentStateManager.lastState)
        assertEquals(
            "persistentStatePut:PREGAME",
            calls.single { it.startsWith("persistentStatePut:") },
        )
        assertTrue(
            calls.indexOfFirst { it.startsWith("emit:") } < calls.indexOfFirst { it.startsWith("persistentStatePut:") },
        )
        assertEquals(GameState.PREGAME, state.state)
        assertTrue(eventBus.emittedTypes.contains(GameResetEvent))
        assertEquals(emptyList(), playerController.restoreCalls)
        assertTrue(tickManager.setFrozenCalls.isNotEmpty())
        assertFalse(tickManager.isFrozen)
    }

    @Test
    fun `legacy reset opt-in still recreates worlds and prepares lobby files`() {
        val calls = mutableListOf<String>()
        val eventBus = RecordingEventBus(calls)
        val state = BingoState(
            isLobbyMode = true,
            state = GameState.POSTGAME,
            options = BingoOptions(cards = emptyList()),
        )
        val playerManager = EmptyPlayerManager
        val playerController = RecordingLobbyPlayerRestorer(calls)
        val teamService = RecordingResetTeamService(calls)
        val scoreboardService = RecordingResetScoreboardService(calls)
        val bossBarService = RecordingResetBossBarService(calls)
        val serverWorldFactory = RecordingServerWorldFactory(calls)
        val menuController = RecordingMenuController(calls)
        val persistentStateManager = RecordingPersistentStateManager(calls)
        val persistentStates = RecordingPersistentStates()
        val tickManager = RecordingTickManager(calls)
        val serverTaskExecutor = ImmediateServerTaskExecutor(calls)
        val log = LoggerFactory.getLogger("ResetServiceTest")

        val service = ResetService(
            eventBus = eventBus,
            state = state,
            playerManager = playerManager,
            playerController = playerController,
            teamService = teamService,
            scoreboardService = scoreboardService,
            bossBarService = bossBarService,
            serverWorldFactory = serverWorldFactory,
            menuController = menuController,
            persistentStateManager = persistentStateManager,
            persistentStates = persistentStates,
            tickManager = tickManager,
            serverTaskExecutor = serverTaskExecutor,
            log = log,
        )

        val propertyName = "exbingo.dynamicWorldRecreateOnReset"
        val oldProperty = System.getProperty(propertyName)
        try {
            System.setProperty(propertyName, "true")
            service.resetGame()
        } finally {
            if (oldProperty == null) {
                System.clearProperty(propertyName)
            } else {
                System.setProperty(propertyName, oldProperty)
            }
        }

        assertTrue(serverWorldFactory.recreateCalled)
        assertEquals(1, menuController.prepareCalls)
        assertEquals(1, menuController.spawnCalls)
        assertEquals(1, persistentStateManager.putCalls)
        assertEquals(GameState.PREGAME, state.state)
    }

    private class RecordingMenuController(
        private val calls: MutableList<String>,
    ) : RuntimeLobbyController {
        var prepareCalls = 0
        var suspendCalls = 0
        var spawnCalls = 0

        override fun prepareLobbyFiles() {
            calls += "prepareLobbyFiles"
            prepareCalls++
        }

        override fun suspendPregameSpawn() {
            calls += "suspendPregameSpawn"
            suspendCalls++
        }

        override fun spawnLobby() {
            calls += "spawnLobby"
            spawnCalls++
        }

        override fun menuEntityStats(): MenuEntityStats {
            return MenuEntityStats()
        }
    }

    private class RecordingLobbyPlayerRestorer(
        private val calls: MutableList<String>,
    ) : LobbyPlayerRestorer {
        val restoreCalls = mutableListOf<String>()

        override fun restoreLobbyPlayerAfterReset(player: IPlayerHandle) {
            calls += "restoreLobbyPlayerAfterReset"
            restoreCalls += player.playerName
        }
    }

    private class RecordingResetTeamService(
        private val calls: MutableList<String>,
    ) : ResetTeamService {
        var clearCalls = 0

        override fun clearTeams() {
            calls += "clearTeams"
            clearCalls++
        }
    }

    private class RecordingResetScoreboardService(
        private val calls: MutableList<String>,
    ) : ResetScoreboardService {
        var clearCalls = 0

        override fun clearScoreboards() {
            calls += "clearScoreboards"
            clearCalls++
        }
    }

    private class RecordingResetBossBarService(
        private val calls: MutableList<String>,
    ) : ResetBossBarService {
        var clearCalls = 0

        override fun clearBossBars() {
            calls += "clearBossBars"
            clearCalls++
        }
    }

    private class RecordingPersistentStateManager(
        private val calls: MutableList<String>,
    ) : IPersistentStateManager {
        var putCalls = 0
        var lastState: GameState? = null

        override fun <T : Any> register(id: String, kType: kotlin.reflect.KType, default: () -> T): IPersistentStateType<T> {
            error("register is not used in this test")
        }

        override fun <T : Any> getFromWorld(type: IPersistentStateType<T>): T {
            error("getFromWorld is not used in this test")
        }

        override fun <T : Any> put(type: IPersistentStateType<T>, value: T) {
            val bingoState = value as BingoState
            calls += "persistentStatePut:${bingoState.state}"
            putCalls++
            lastState = bingoState.state
        }
    }

    private object RecordingPersistentStateType : IPersistentStateType<BingoState>

    private class RecordingPersistentStates : ResetPersistentStates {
        override val bingo: IPersistentStateType<BingoState> = RecordingPersistentStateType
    }

    private class RecordingServerWorldFactory(
        private val calls: MutableList<String>,
    ) : IServerWorldFactory {
        var recreateCalled = false

        override val overworld: IServerWorld
            get() = error("overworld is not used in this test")

        override fun forWorld(world: net.minecraft.server.level.ServerLevel): IServerWorld {
            error("forWorld is not used in this test")
        }

        override fun listWorlds(): List<IServerWorld> {
            return emptyList()
        }

        override fun recreateWorlds(seed: Long, reattachPlayers: Boolean, callback: () -> Unit) {
            calls += "recreateWorlds"
            recreateCalled = true
            callback()
        }
    }

    private class RecordingTickManager(
        private val calls: MutableList<String>,
    ) : ITickManager {
        private var frozen = false
        override val isFrozen: Boolean
            get() = frozen
        override val runsNormally: Boolean = true
        val setFrozenCalls = mutableListOf<Boolean>()

        override fun setFrozen(frozen: Boolean) {
            calls += "setFrozen:$frozen"
            this.frozen = frozen
            setFrozenCalls += frozen
        }
    }

    private class ImmediateServerTaskExecutor(
        private val calls: MutableList<String>,
    ) : IExecutors.IServerTaskExecutor {
        override fun execute(command: Runnable) {
            calls += "execute"
            command.run()
        }

        override fun executeNextTick(runnable: Runnable) {
            calls += "executeNextTick"
            runnable.run()
        }
    }

    private class RecordingEventBus(
        private val calls: MutableList<String>,
    ) : IEventBus {
        val emittedTypes = mutableListOf<IReturnEvent<*, *>>()

        override fun <T : Any, R> register(type: IReturnEvent<T, R>, callback: Function<T, R>): ICallbackHandle {
            calls += "register:${type.name}"
            return object : ICallbackHandle {
                override fun close() = Unit
            }
        }

        override fun <T : Any, R> emit(type: IReturnEvent<T, R>, event: T): List<R> {
            calls += "emit:${type.name}"
            emittedTypes += type
            return emptyList()
        }
    }

    private object EmptyPlayerManager : IPlayerManager {
        override fun forPlayer(player: net.minecraft.server.level.ServerPlayer): IPlayerHandle {
            error("forPlayer is not used in this test")
        }

        override fun getPlayer(uuid: UUID): IPlayerHandle? {
            return null
        }

        override fun getPlayers(): List<IPlayerHandle> {
            return emptyList()
        }

        override fun getOfflinePlayer(profile: me.jfenn.bingo.platform.player.PlayerProfile): IPlayerHandle {
            error("getOfflinePlayer is not used in this test")
        }

        override fun updatePlayerListName(player: IPlayerHandle) {
            error("updatePlayerListName is not used in this test")
        }

        override fun playToAround(player: net.minecraft.server.level.ServerPlayer?, sound: me.jfenn.bingo.platform.PlayerSoundEvent, category: me.jfenn.bingo.platform.PlayerSoundCategory, volume: Float, pitch: Float, position: Triple<Double, Double, Double>, world: net.minecraft.server.level.ServerLevel) {
            error("playToAround is not used in this test")
        }

        override fun broadcastChatMessage(message: me.jfenn.bingo.platform.commands.ISignedMessage, sender: IPlayerHandle) {
            error("broadcastChatMessage is not used in this test")
        }
    }
}
