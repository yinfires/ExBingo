package me.jfenn.bingo.common.state

import me.jfenn.bingo.common.event.EventBus
import me.jfenn.bingo.common.event.model.StateChangedEvent
import me.jfenn.bingo.common.options.BingoOptions
import me.jfenn.bingo.common.utils.json
import me.jfenn.bingo.platform.IJsonSerializers
import me.jfenn.bingo.platform.IServerWorld
import me.jfenn.bingo.platform.IServerWorldFactory
import me.jfenn.bingo.platform.event.model.ApplicationCloseEvent
import me.jfenn.bingo.platform.register
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import java.time.Duration
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersistentStateManagerTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `state changes are written immediately`() {
        val eventBus = EventBus(LoggerFactory.getLogger("PersistentStateManagerTest"))
        val manager = createManager(eventBus)
        val type = registerBingoState(manager)
        val state = manager.getFromWorld(type)
        state.state = GameState.PLAYING

        eventBus.emit(StateChangedEvent, StateChangedEvent(GameState.COUNTDOWN, GameState.PLAYING))

        assertEquals(GameState.PLAYING, readSavedState().state)
    }

    @Test
    fun `application close writes current state before the scope stops`() {
        val eventBus = EventBus(LoggerFactory.getLogger("PersistentStateManagerTest"))
        val manager = createManager(eventBus)
        val type = registerBingoState(manager)
        val state = manager.getFromWorld(type)
        state.state = GameState.PLAYING

        eventBus.emit(ApplicationCloseEvent, Unit)

        assertEquals(GameState.PLAYING, readSavedState().state)
    }

    @Test
    fun `reads backup when primary state file is corrupt`() {
        val eventBus = EventBus(LoggerFactory.getLogger("PersistentStateManagerTest"))
        val manager = createManager(eventBus)
        val type = registerBingoState(manager)
        val state = manager.getFromWorld(type)
        state.state = GameState.PLAYING
        state.options.timeLimit = Duration.ofMinutes(10)
        manager.put(type, state)

        state.state = GameState.POSTGAME
        state.options.timeLimit = Duration.ofMinutes(20)
        manager.put(type, state)
        Files.write(savedStatePath(), byteArrayOf(1, 2, 3, 4))

        val restoredManager = createManager(EventBus(LoggerFactory.getLogger("PersistentStateManagerTest")))
        val restoredType = registerBingoState(restoredManager)
        val restoredState = restoredManager.getFromWorld(restoredType)

        assertEquals(GameState.PLAYING, restoredState.state)
        assertEquals(Duration.ofMinutes(10), restoredState.options.timeLimit)
    }

    @Test
    fun `corrupt primary state file does not overwrite readable backup`() {
        val eventBus = EventBus(LoggerFactory.getLogger("PersistentStateManagerTest"))
        val manager = createManager(eventBus)
        val type = registerBingoState(manager)
        val state = manager.getFromWorld(type)
        state.state = GameState.PLAYING
        state.options.timeLimit = Duration.ofMinutes(10)
        manager.put(type, state)

        state.state = GameState.POSTGAME
        state.options.timeLimit = Duration.ofMinutes(20)
        manager.put(type, state)
        Files.write(savedStatePath(), byteArrayOf(1, 2, 3, 4))

        state.state = GameState.PREGAME
        state.options.timeLimit = Duration.ofMinutes(30)
        manager.put(type, state)

        Files.write(savedStatePath(), byteArrayOf(5, 6, 7, 8))

        val restoredManager = createManager(EventBus(LoggerFactory.getLogger("PersistentStateManagerTest")))
        val restoredType = registerBingoState(restoredManager)
        val restoredState = restoredManager.getFromWorld(restoredType)

        assertEquals(GameState.PLAYING, restoredState.state)
        assertEquals(Duration.ofMinutes(10), restoredState.options.timeLimit)
    }

    private fun createManager(eventBus: EventBus): PersistentStateManager {
        return PersistentStateManager(
            logger = LoggerFactory.getLogger("PersistentStateManagerTest"),
            serializers = TestJsonSerializers,
            serverWorldFactory = TestServerWorldFactory(tempDir),
            eventBus = eventBus,
        )
    }

    private fun registerBingoState(manager: PersistentStateManager) = manager.register<BingoState>("exbingo") {
        BingoState(options = BingoOptions(cards = emptyList()))
    }

    private fun savedStatePath(): Path {
        return tempDir.resolve("data").resolve("exbingo.json.gz")
    }

    private fun readSavedState(): BingoState {
        val path = savedStatePath()
        assertTrue(Files.exists(path), "persistent state file should exist")
        return Files.newInputStream(path).buffered()
            .let { GZIPInputStream(it) }
            .use { json.decodeFromString(BingoState.serializer(), it.readBytes().decodeToString()) }
    }

    private object TestJsonSerializers : IJsonSerializers {
        override val json = me.jfenn.bingo.common.utils.json
    }

    private class TestServerWorldFactory(
        private val root: Path,
    ) : IServerWorldFactory {
        override val overworld: IServerWorld = object : IServerWorld {
            override val identifier: String = "minecraft:overworld"
            override val directory: Path = root
            override val world: net.minecraft.server.level.ServerLevel
                get() = error("world is not used in this test")
            override val worldBorder: me.jfenn.bingo.platform.IWorldBorder
                get() = error("worldBorder is not used in this test")
            override val coordinateScale: Double = 1.0
            override val logicalHeight: Int = 384
            override val bottomY: Int = -64
            override val seaLevel: Int = 63
            override val spawnPos: me.jfenn.bingo.platform.block.BlockPosition =
                me.jfenn.bingo.platform.block.BlockPosition(0, 64, 0)
            override val hasCeiling: Boolean = false
            override val isOverworld: Boolean = true
            override var timeOfDay: Long = 0
            override fun getBlockState(pos: me.jfenn.bingo.platform.block.BlockPosition): me.jfenn.bingo.platform.block.IBlockState {
                error("getBlockState is not used in this test")
            }
            override fun getBiome(pos: me.jfenn.bingo.platform.block.BlockPosition): me.jfenn.bingo.platform.IRegistryEntry.Biome {
                error("getBiome is not used in this test")
            }
            override fun addTicket(chunk: Pair<Int, Int>): IServerWorld.IChunkTicketHandle {
                error("addTicket is not used in this test")
            }
            override fun getChunkSync(chunk: Pair<Int, Int>): me.jfenn.bingo.platform.world.IChunk {
                error("getChunkSync is not used in this test")
            }
            override fun getChunkAsync(chunk: Pair<Int, Int>): java.util.concurrent.CompletableFuture<me.jfenn.bingo.platform.world.IChunk?> {
                error("getChunkAsync is not used in this test")
            }
            override fun areChunkEntitiesReady(chunk: Pair<Int, Int>): Boolean {
                error("areChunkEntitiesReady is not used in this test")
            }
            override fun close() = Unit
        }

        override fun forWorld(world: net.minecraft.server.level.ServerLevel): IServerWorld {
            error("forWorld is not used in this test")
        }

        override fun listWorlds(): List<IServerWorld> = listOf(overworld)

        override fun recreateWorlds(seed: Long, reattachPlayers: Boolean, callback: () -> Unit) {
            error("recreateWorlds is not used in this test")
        }
    }
}
