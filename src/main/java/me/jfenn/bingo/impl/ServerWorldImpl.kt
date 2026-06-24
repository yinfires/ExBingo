package me.jfenn.bingo.impl

import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.utils.measureTime
import me.jfenn.bingo.impl.block.BlockStateImpl
import me.jfenn.bingo.impl.world.ChunkImpl
import me.jfenn.bingo.mixin.LevelPropertiesAccessor
import me.jfenn.bingo.mixinhelper.ServerChunkManagerMixinHelper
import me.jfenn.bingo.platform.IRegistryEntry
import me.jfenn.bingo.platform.IServerWorld
import me.jfenn.bingo.platform.IServerWorldFactory
import me.jfenn.bingo.platform.IWorldBorder
import me.jfenn.bingo.platform.block.BlockPosition
import me.jfenn.bingo.platform.block.IBlockState
import me.jfenn.bingo.platform.world.IChunk
import net.minecraft.world.level.dimension.end.EndDragonFight
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.TicketType
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket
import net.minecraft.network.protocol.game.ClientboundRespawnPacket
import net.minecraft.world.entity.Entity
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Unit
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.border.WorldBorder
import net.minecraft.world.level.dimension.DimensionType
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.storage.PrimaryLevelData
import net.minecraft.world.level.storage.LevelResource
import org.slf4j.Logger
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletableFuture

class ServerWorldFactory(
    private val log: Logger,
    private val server: MinecraftServer,
) : IServerWorldFactory {
    override val overworld: IServerWorld
        get() = forWorld(server.overworld())

    override fun forWorld(world: ServerLevel): IServerWorld = ServerWorldImpl(world)
    override fun listWorlds(): List<IServerWorld> {
        return server.allLevels.map { forWorld(it) }
    }

    private fun tickKeepAlive() {
        server.connection.tick()
    }

    override fun recreateWorlds(
        seed: Long,
        callback: () -> kotlin.Unit
    ) {
        tickKeepAlive()

        // Remove scoreboard objectives to avoid a crash when data is reloaded in loadWorld()
        server.scoreboard.objectives
            .toList()
            .forEach {
                try {
                    server.scoreboard.removeObjective(it)
                } catch (e: Throwable) {
                    log.error("[Reset] Error removing objective:", e)
                }
            }

        // Clear any ongoing raids
        server.allLevels.forEach { world ->
            world.raids.raidManagerAccessor.raids.values
                .forEach { it.stop() }
        }

        server.accessor.setSaving(true)
        try {
            // First, ensure the world/data is saved
            log.measureTime("[Reset] Saving...") {
                // MinecraftServer::saveAll
                server.playerList.saveAll()

                for (world in listWorlds()) {
                    // ServerLevel::save -> ServerLevel::saveLevel
                    val serverWorld: ServerLevel = world.world
                    serverWorld.dataStorage.save()
                }
            }

            tickKeepAlive()

            server.threadExecutorAccessor.invokeCancelTasks()

            // Use mixins to skip save() calls within world.close(), so that it
            // *only* closes resources without waiting to write to disk
            ServerChunkManagerMixinHelper.shouldCancelSaving = true

            for (world in listWorlds()) {
                log.measureTime("[Reset] Closing ${world.identifier}...") {
                    world.close()
                }

                tickKeepAlive()

                arrayOf("region", "poi", "entities")
                    .map { world.directory.resolve(it).toFile() }
                    .filter { it.exists() }
                    .onEach { it.deleteRecursively() }

                tickKeepAlive()
            }

            callback()
            tickKeepAlive()

            log.measureTime("[Reset] Loading new worlds...") {
                val levelData = server.worldData as PrimaryLevelData
                val levelPropertiesAccessor = levelData as LevelPropertiesAccessor
                levelData.setEndDragonFightData(EndDragonFight.Data.DEFAULT)
                levelPropertiesAccessor.worldOptions = levelPropertiesAccessor.worldOptions.withSeed(OptionalLong.of(seed))
        server.accessor.invokeLoadLevel()
            }
            tickKeepAlive()

            // invokeLoadLevel() replaces every ServerLevel with a brand-new instance, but the
            // online ServerPlayer objects still reference the OLD (now closed/orphaned) levels and
            // are still tracked by them. If we don't migrate them, players end up desynced from the
            // world they're rendered into: the server processes their actions (e.g. breaking grass
            // drops seeds) but never sends back chunk/entity updates, so blocks appear to revert,
            // mobs freeze in place, and the player can be invisible to themselves.
            //
            // ServerPlayer.teleportTo()/changeDimension() does NOT fix this on its own: when the
            // target dimension key matches the player's current (stale) level key it takes an early
            // return that only repositions, without re-attaching to the new level object. So we
            // re-attach every player to the new level explicitly here.
            log.measureTime("[Reset] Re-attaching players to new worlds...") {
                for (player in server.playerList.players.toList()) {
                    try {
                        reattachPlayerToNewLevel(player)
                    } catch (e: Throwable) {
                        log.error("[Reset] Failed to re-attach player ${player.scoreboardName} to the new world", e)
                    }
                }
            }
            tickKeepAlive()

            // invoke "#minecraft:load" again, as scoreboards will be reset
            with(server.functions) {
                getTag(ResourceLocation.parse("minecraft:load"))
                    .forEach { execute(it, gameLoopSender) }
            }
        } finally {
            server.accessor.setSaving(false)
            ServerChunkManagerMixinHelper.shouldCancelSaving = false
        }
    }

    /**
     * Migrates a player from their stale (pre-reset) ServerLevel to the freshly created
     * level with the same dimension key, mirroring what ServerPlayer.changeDimension does
     * for a cross-dimension move. Without this the player stays tracked by the old, closed
     * level and is fully desynced from the world they're rendered into.
     */
    private fun reattachPlayerToNewLevel(player: ServerPlayer) {
        val oldLevel = player.serverLevel()
        val newLevel = server.getLevel(oldLevel.dimension())
        if (newLevel == null) {
            log.error("[Reset] No new level found for ${player.scoreboardName} in dimension ${oldLevel.dimension().location()}")
            return
        }
        if (newLevel === oldLevel) {
            log.warn("[Reset] ${player.scoreboardName} is already on the new level object - skipping re-attach (this is unexpected)")
            return
        }

        player.stopRiding()

        val levelData = newLevel.levelData
        val playerList = server.playerList
        player.connection.send(ClientboundRespawnPacket(player.createCommonSpawnInfo(newLevel), ClientboundRespawnPacket.KEEP_ALL_DATA))
        player.connection.send(ClientboundChangeDifficultyPacket(levelData.difficulty, levelData.isDifficultyLocked))
        playerList.sendPlayerPermissionLevel(player)

        // Remove from the old (orphaned) level's tracking
        oldLevel.removePlayerImmediately(player, Entity.RemovalReason.CHANGED_DIMENSION)
        player.revive()

        // Attach to the new level and re-add to its tracking (this re-registers the player in
        // the new level's chunk/entity managers and distance maps, so chunks load & tick again).
        player.setServerLevel(newLevel)
        player.connection.teleport(player.x, player.y, player.z, player.yRot, player.xRot)
        player.connection.resetPosition()
        newLevel.addDuringTeleport(player)

        player.connection.send(ClientboundPlayerAbilitiesPacket(player.abilities))
        playerList.sendLevelInfo(player, newLevel)
        playerList.sendAllPlayerInfo(player)
        playerList.sendActivePlayerEffects(player)
        net.neoforged.neoforge.attachment.AttachmentSync.syncInitialPlayerAttachments(player)
    }
}

class ServerWorldImpl(
    override val world: ServerLevel
): IServerWorld {
    companion object {
        private val TICKET_HELD = TicketType.create("$MOD_ID_BINGO-held") { _: Any?, _: Any? -> 0 }
        private val TICKET_ASYNC = TicketType.create("$MOD_ID_BINGO-async") { _: Any?, _: Any? -> 0 }
    }

    override val identifier: String
        get() = world.dimension().location().toString()

    override val directory: Path
        get() = DimensionType.getStorageFolder(world.dimension(), world.server.getWorldPath(LevelResource.ROOT))

    override val worldBorder: IWorldBorder
        get() = WorldBorderImpl(world.server.overworld().worldBorder)
    override val coordinateScale: Double
        get() = world.dimensionType().coordinateScale()
    override val logicalHeight: Int
        get() = world.logicalHeight
    override val bottomY: Int
        get() = world.minBuildHeight
    override val seaLevel: Int
        get() = world.seaLevel
    override val spawnPos: BlockPosition
        get() = BlockPosition.fromBlockPos(world.sharedSpawnPos)
    override val hasCeiling: Boolean
        get() = world.dimensionType().hasCeiling()
    override val isOverworld: Boolean
        get() = world.dimension() == Level.OVERWORLD

    override var timeOfDay: Long
        get() = world.dayTime()
        set(value) { world.setDayTime(value) }

    override fun getBlockState(pos: BlockPosition): IBlockState {
        return world.getBlockState(pos.toBlockPos())
            .let { BlockStateImpl.fromBlockState(it) }
    }

    override fun getBiome(pos: BlockPosition): IRegistryEntry.Biome {
        return BiomeRegistryEntry(world.getBiome(pos.toBlockPos()))
    }

    private val taskExecutor = Executors.createServerTaskExecutor(world.server)

    override fun addTicket(chunk: Pair<Int, Int>): IServerWorld.IChunkTicketHandle {
        world.chunkSource.addRegionTicket(TICKET_HELD, ChunkPos(chunk.first, chunk.second), 0, Unit.INSTANCE)
        return ChunkTicketHandle(chunk)
    }

    inner class ChunkTicketHandle(
        private val chunk: Pair<Int, Int>
    ) : IServerWorld.IChunkTicketHandle {
        override fun close() {
            world.chunkSource.removeRegionTicket(TICKET_HELD, ChunkPos(chunk.first, chunk.second), 0, Unit.INSTANCE)
        }
    }

    override fun getChunkSync(chunk: Pair<Int, Int>): IChunk {
        return ChunkImpl(world.getChunk(chunk.first, chunk.second))
    }

    override fun getChunkAsync(chunk: Pair<Int, Int>): CompletableFuture<IChunk?> {
        if (!world.server.isSameThread) {
            return CompletableFuture.supplyAsync({ getChunkAsync(chunk) }, world.server)
                .thenCompose({ it })
        }

        // Add a ticket for the chunk
        val chunkManager = world.chunkSource
        val chunkPos = ChunkPos(chunk.first, chunk.second)
        chunkManager.addRegionTicket(TICKET_ASYNC, chunkPos, 0, Unit.INSTANCE)

        val chunkFuture = chunkManager.getChunkFuture(chunk.first, chunk.second, ChunkStatus.FULL, true)
            .thenApply { it.orElse(null) }

        // Remove the chunk ticket once loaded
        chunkFuture.whenCompleteAsync({ _, _ ->
            chunkManager.removeRegionTicket(TICKET_ASYNC, chunkPos, 0, Unit.INSTANCE)
        }, taskExecutor)

        return chunkFuture.thenApply { if (it != null) ChunkImpl(it) else null }
    }

    override fun close() {
        world.close()
    }
}

class WorldBorderImpl(
    val worldBorder: WorldBorder
): IWorldBorder {
    override val centerX: Double by worldBorder::centerX
    override val centerZ: Double by worldBorder::centerZ
    override val maxRadius: Int
        get() = worldBorder.absoluteMaxSize
    override fun contains(blockPos: BlockPosition): Boolean = worldBorder.isWithinBounds(blockPos.toBlockPos())
}
