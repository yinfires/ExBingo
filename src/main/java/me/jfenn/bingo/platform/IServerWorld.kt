package me.jfenn.bingo.platform

import me.jfenn.bingo.platform.block.BlockPosition
import me.jfenn.bingo.platform.block.IBlockState
import me.jfenn.bingo.platform.world.IChunk
import net.minecraft.server.level.ServerLevel
import java.io.Closeable
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

interface IServerWorldFactory {
    val overworld: IServerWorld
    fun forWorld(world: ServerLevel): IServerWorld
    fun listWorlds(): List<IServerWorld>
    fun recreateWorlds(seed: Long, reattachPlayers: Boolean = true, callback: () -> Unit)
}

interface IServerWorld {

    val identifier: String

    val directory: Path

    val world: ServerLevel

    val worldBorder: IWorldBorder
    val coordinateScale: Double
    val logicalHeight: Int
    val bottomY: Int
    val seaLevel: Int
    val spawnPos: BlockPosition
    val hasCeiling: Boolean
    val isOverworld: Boolean

    var timeOfDay: Long

    fun getBlockState(pos: BlockPosition): IBlockState
    fun getBiome(pos: BlockPosition): IRegistryEntry.Biome

    fun addTicket(chunk: Pair<Int, Int>): IChunkTicketHandle

    fun getChunkSync(chunk: Pair<Int, Int>): IChunk
    fun getChunkAsync(chunk: Pair<Int, Int>): CompletableFuture<IChunk?>

    /**
     * True once the entities persisted in [chunk] have been promoted out of the async loading
     * inbox and into the live entity list (i.e. [ServerLevel.allEntities] can actually see them).
     *
     * Loading a chunk via [getChunkSync] returns as soon as the *blocks* are ready, but entities
     * are deserialized asynchronously a tick or more later. Cleanup logic that scans `allEntities`
     * right after loading a chunk therefore misses still-pending entities, which then surface a
     * tick later and overlap freshly spawned ones. Callers that must reconcile against persisted
     * entities should wait for this to return true first.
     */
    fun areChunkEntitiesReady(chunk: Pair<Int, Int>): Boolean

    fun close()

    interface IChunkTicketHandle : Closeable
}

interface IWorldBorder {
    val centerX: Double
    val centerZ: Double
    val maxRadius: Int
    fun contains(blockPos: BlockPosition): Boolean
}
