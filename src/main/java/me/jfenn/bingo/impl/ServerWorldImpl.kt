package me.jfenn.bingo.impl

import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.utils.measureTime
import me.jfenn.bingo.impl.block.BlockStateImpl
import me.jfenn.bingo.impl.world.ChunkImpl
import me.jfenn.bingo.mixin.ChunkMapAccessor
import me.jfenn.bingo.mixin.LevelPropertiesAccessor
import me.jfenn.bingo.mixin.PersistentEntitySectionManagerAccessor
import me.jfenn.bingo.mixin.ServerChunkManagerAccessor
import me.jfenn.bingo.mixin.ServerLevelEntityManagerAccessor
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
import net.minecraft.util.Unit as MinecraftUnit
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.border.WorldBorder
import net.minecraft.world.level.dimension.DimensionType
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.entity.Visibility
import net.minecraft.world.level.storage.PrimaryLevelData
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.neoforge.event.EventHooks
import org.slf4j.Logger
import java.nio.file.Path
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.CompletableFuture

class ServerWorldFactory(
    private val log: Logger,
    private val server: MinecraftServer,
) : IServerWorldFactory {
    private companion object {
        // Per-dimension SavedData files (under <world>/data/) that must be wiped on world recreation so
        // they don't leak progress into the next round. Matched against every recreated world's data dir.
        val WORLD_DATA_FILES_TO_RESET = listOf(
            "book_progressions.dat", // Eternal Starlight gatekeeper permit store (overworld)
        )
    }

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
        reattachPlayers: Boolean,
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

                // Per-dimension SavedData that must NOT carry over into the next round. These live under
                // <world.directory>/data/. Deleting the file before invokeLoadLevel() makes the fresh
                // DimensionDataStorage re-create empty state via computeIfAbsent.
                //   - book_progressions.dat: Eternal Starlight's gatekeeper permit store (keyed by player
                //     UUID, stored in the OVERWORLD DataStorage). Without this, players who were permitted
                //     by The Gatekeeper last round can still trade with it in the next round. The per-player
                //     advancement is already cleared on reset, but this world-level store is separate.
                WORLD_DATA_FILES_TO_RESET
                    .map { world.directory.resolve("data").resolve(it).toFile() }
                    .filter { it.exists() }
                    .onEach {
                        log.info("[Reset] Deleting stale world data file {}", it)
                        it.delete()
                    }

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
                // NeoForge caches the level array used by MinecraftServer.tickChildren().
                // loadLevel() replaces the map entries but does not invalidate that cache, so without
                // this the server keeps ticking the closed worlds and the new entity managers never
                // drain their loading inbox.
                server.markWorldsDirty()
            }
            tickKeepAlive()

            if (reattachPlayers) {
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
                            logPlayerAttachment("after reattach", player)
                        } catch (e: Throwable) {
                            log.error("[Reset] Failed to re-attach player ${player.scoreboardName} to the new world", e)
                        }
                    }
                }
                tickKeepAlive()
                validatePlayerAttachments("after reattach pass")
                server.execute {
                    validatePlayerAttachments("next tick after reattach")
                }
            }

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

    private fun validatePlayerAttachments(stage: String) {
        for (player in server.playerList.players.toList()) {
            logPlayerAttachment(stage, player)
        }
    }

    private fun logPlayerAttachment(stage: String, player: ServerPlayer) {
        val playerLevel = player.serverLevel()
        val currentLevel = server.getLevel(playerLevel.dimension())
        val diagnostics = collectPlayerLevelAttachmentDiagnostics(player)
        val chunkPos = player.chunkPosition()
        val message = "[ResetDiag] {} player={} dimension={} playerLevel={} currentLevel={} attached={} inLevelPlayers={} entityTracked={} entitiesLoaded={} entityTicking={} blockTicking={} distanceEntityTicking={} distanceBlockTicking={} chunkMapWatching={} entityLoadStatus={} entityVisibility={} entityLoadingInboxSize={} gameMode={} chunk=({}, {}) pos=({}, {}, {})"
        val args = arrayOf(
            stage,
            player.scoreboardName,
            playerLevel.dimension().location(),
            System.identityHashCode(playerLevel),
            currentLevel?.let { System.identityHashCode(it) },
            diagnostics.attached,
            diagnostics.inLevelPlayers,
            diagnostics.entityTracked,
            diagnostics.entitiesLoaded,
            diagnostics.positionEntityTicking,
            diagnostics.blockTicking,
            diagnostics.distanceEntityTicking,
            diagnostics.distanceBlockTicking,
            diagnostics.chunkMapWatching,
            diagnostics.entityLoadStatus,
            diagnostics.entityVisibility,
            diagnostics.entityLoadingInboxSize,
            player.gameMode.gameModeForPlayer,
            chunkPos.x,
            chunkPos.z,
            player.x,
            player.y,
            player.z,
        )
        if (isPlayerLevelAttachmentHealthy(diagnostics)) {
            log.info(message, *args)
        } else {
            if (diagnostics.distanceEntityTicking && diagnostics.distanceBlockTicking && (!diagnostics.entitiesLoaded || !diagnostics.positionEntityTicking)) {
                log.error(
                    "[ResetDiag] ERROR entity manager chunk lifecycle not ready: player={} chunk=({}, {}) entityLoadStatus={} entityVisibility={} loadingInboxSize={}",
                    player.scoreboardName,
                    chunkPos.x,
                    chunkPos.z,
                    diagnostics.entityLoadStatus,
                    diagnostics.entityVisibility,
                    diagnostics.entityLoadingInboxSize,
                )
            }
            log.error(message, *args)
        }
    }

    private fun collectPlayerLevelAttachmentDiagnostics(player: ServerPlayer): PlayerLevelAttachmentDiagnostics {
        val playerLevel = player.serverLevel()
        val currentLevel = server.getLevel(playerLevel.dimension())
        val blockPos = player.blockPosition()
        val chunkPos = player.chunkPosition()
        val chunkLong = ChunkPos.asLong(blockPos)
        val distanceManager = playerLevel.chunkSource.chunkMap.getDistanceManager()
        val entityLifecycle = collectEntityChunkLifecycleDiagnostics(playerLevel, chunkLong)
        return PlayerLevelAttachmentDiagnostics(
            attached = currentLevel === playerLevel,
            inLevelPlayers = playerLevel.players().contains(player),
            entityTracked = playerLevel.getEntity(player.uuid) === player,
            entitiesLoaded = playerLevel.areEntitiesLoaded(chunkLong),
            positionEntityTicking = playerLevel.isPositionEntityTicking(blockPos),
            blockTicking = playerLevel.shouldTickBlocksAt(chunkLong),
            distanceEntityTicking = distanceManager.inEntityTickingRange(chunkLong),
            distanceBlockTicking = distanceManager.inBlockTickingRange(chunkLong),
            chunkMapWatching = playerLevel.chunkSource.chunkMap.getPlayers(chunkPos, false).contains(player),
            entityLoadStatus = entityLifecycle.loadStatus,
            entityVisibility = entityLifecycle.visibility,
            entityLoadingInboxSize = entityLifecycle.loadingInboxSize,
        )
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
        logPlayerAttachment("before reattach", player)
        if (newLevel == null) {
            log.error("[Reset] No new level found for ${player.scoreboardName} in dimension ${oldLevel.dimension().location()}")
            return
        }
        if (newLevel === oldLevel) {
            log.warn("[Reset] ${player.scoreboardName} is already on the new level object - skipping re-attach (this is unexpected)")
            return
        }

        val levelData = newLevel.levelData
        val playerList = server.playerList
        val oldDimension = oldLevel.dimension()
        val newDimension = newLevel.dimension()
        val chunkTrackingOps = ServerLevelPlayerChunkTrackingOps(
            level = newLevel,
            player = player,
            chunkPos = player.chunkPosition(),
        )
        runNeoForgePlayerReattachSequence(object : NeoForgePlayerReattachOps {
            override fun addPostTeleportTicket() {
                chunkTrackingOps.addPostTeleportTicket()
            }

            override fun runDistanceManagerUpdates() {
                chunkTrackingOps.runDistanceManagerUpdates()
            }

            override fun stopRiding() {
                player.stopRiding()
            }

            override fun sendRespawnPacket() {
                player.connection.send(ClientboundRespawnPacket(player.createCommonSpawnInfo(newLevel), ClientboundRespawnPacket.KEEP_ALL_DATA))
            }

            override fun sendDifficulty() {
                player.connection.send(ClientboundChangeDifficultyPacket(levelData.difficulty, levelData.isDifficultyLocked))
            }

            override fun sendPermission() {
                playerList.sendPlayerPermissionLevel(player)
            }

            override fun removeFromOldLevel() {
                oldLevel.removePlayerImmediately(player, Entity.RemovalReason.CHANGED_DIMENSION)
            }

            override fun revive() {
                player.revive()
            }

            override fun setServerLevel() {
                player.setServerLevel(newLevel)
            }

            override fun teleportConnection() {
                player.connection.teleport(player.x, player.y, player.z, player.yRot, player.xRot)
            }

            override fun resetConnectionPosition() {
                player.connection.resetPosition()
            }

            override fun addDuringTeleport() {
                newLevel.addDuringTeleport(player)
            }

            override fun updatePlayerStatus() {
                chunkTrackingOps.updatePlayerStatus()
            }

            override fun moveChunkSource() {
                chunkTrackingOps.moveChunkSource()
            }

            override fun sendAbilities() {
                player.connection.send(ClientboundPlayerAbilitiesPacket(player.abilities))
            }

            override fun sendLevelInfo() {
                playerList.sendLevelInfo(player, newLevel)
            }

            override fun sendAllPlayerInfo() {
                playerList.sendAllPlayerInfo(player)
            }

            override fun sendActiveEffects() {
                playerList.sendActivePlayerEffects(player)
            }

            override fun syncAttachments() {
                net.neoforged.neoforge.attachment.AttachmentSync.syncInitialPlayerAttachments(player)
            }

            override fun markDimensionChanged() {
                player.hasChangedDimension()
            }

            override fun fireChangedDimensionEvent() {
                EventHooks.firePlayerChangedDimensionEvent(player, oldDimension, newDimension)
            }
        })
    }
}

internal data class PlayerLevelAttachmentDiagnostics(
    val attached: Boolean,
    val inLevelPlayers: Boolean,
    val entityTracked: Boolean,
    val entitiesLoaded: Boolean,
    val positionEntityTicking: Boolean,
    val blockTicking: Boolean,
    val distanceEntityTicking: Boolean,
    val distanceBlockTicking: Boolean,
    val chunkMapWatching: Boolean,
    val entityLoadStatus: String = ENTITY_CHUNK_LOAD_STATUS_LOADED,
    val entityVisibility: String = ENTITY_CHUNK_VISIBILITY_TICKING,
    val entityLoadingInboxSize: Int = 0,
)

internal fun isPlayerLevelAttachmentHealthy(diagnostics: PlayerLevelAttachmentDiagnostics): Boolean {
    return diagnostics.attached &&
        diagnostics.inLevelPlayers &&
        diagnostics.entityTracked &&
        diagnostics.entitiesLoaded &&
        diagnostics.positionEntityTicking &&
        diagnostics.blockTicking &&
        diagnostics.distanceEntityTicking &&
        diagnostics.distanceBlockTicking &&
        diagnostics.chunkMapWatching &&
        isEntityChunkLifecycleHealthy(diagnostics.entityLoadStatus, diagnostics.entityVisibility)
}

internal interface PlayerChunkTrackingOps {
    fun addPostTeleportTicket()
    fun runDistanceManagerUpdates()
    fun updatePlayerStatus()
    fun moveChunkSource()
}

internal fun preparePlayerChunkTracking(ops: PlayerChunkTrackingOps) {
    ops.addPostTeleportTicket()
    ops.runDistanceManagerUpdates()
}

internal fun finishPlayerChunkTracking(ops: PlayerChunkTrackingOps) {
    ops.updatePlayerStatus()
    ops.moveChunkSource()
    ops.runDistanceManagerUpdates()
}

internal class ServerLevelPlayerChunkTrackingOps(
    private val level: ServerLevel,
    private val player: ServerPlayer,
    private val chunkPos: ChunkPos,
) : PlayerChunkTrackingOps {
    override fun addPostTeleportTicket() {
        level.chunkSource.addRegionTicket(TicketType.POST_TELEPORT, chunkPos, 1, player.id)
    }

    override fun runDistanceManagerUpdates() {
        (level.chunkSource as ServerChunkManagerAccessor).invokeUpdateChunks()
        kotlin.Unit
    }

    override fun updatePlayerStatus() {
        (level.chunkSource.chunkMap as ChunkMapAccessor).invokeUpdatePlayerStatus(player, true)
    }

    override fun moveChunkSource() {
        level.chunkSource.move(player)
    }
}

internal interface NeoForgePlayerReattachOps : PlayerChunkTrackingOps {
    fun stopRiding()
    fun sendRespawnPacket()
    fun sendDifficulty()
    fun sendPermission()
    fun removeFromOldLevel()
    fun revive()
    fun setServerLevel()
    fun teleportConnection()
    fun resetConnectionPosition()
    fun addDuringTeleport()
    fun sendAbilities()
    fun sendLevelInfo()
    fun sendAllPlayerInfo()
    fun sendActiveEffects()
    fun syncAttachments()
    fun markDimensionChanged()
    fun fireChangedDimensionEvent()
}

internal fun runNeoForgePlayerReattachSequence(ops: NeoForgePlayerReattachOps) {
    preparePlayerChunkTracking(ops)
    ops.stopRiding()
    ops.sendRespawnPacket()
    ops.sendDifficulty()
    ops.sendPermission()
    ops.removeFromOldLevel()
    ops.revive()
    ops.setServerLevel()
    ops.teleportConnection()
    ops.resetConnectionPosition()
    ops.addDuringTeleport()
    finishPlayerChunkTracking(ops)
    ops.sendAbilities()
    ops.sendLevelInfo()
    ops.sendAllPlayerInfo()
    ops.sendActiveEffects()
    ops.syncAttachments()
    ops.markDimensionChanged()
    ops.fireChangedDimensionEvent()
}

internal data class EntityChunkLifecycleDiagnostics(
    val loadStatus: String,
    val visibility: String,
    val loadingInboxSize: Int,
)

internal fun isEntityChunkLifecycleHealthy(loadStatus: String, visibility: String): Boolean {
    return loadStatus == ENTITY_CHUNK_LOAD_STATUS_LOADED && visibility == ENTITY_CHUNK_VISIBILITY_TICKING
}

internal fun collectEntityChunkLifecycleDiagnostics(
    level: ServerLevel,
    chunkLong: Long,
): EntityChunkLifecycleDiagnostics {
    val entityManager = (level as ServerLevelEntityManagerAccessor).bingoEntityManager
    val accessor = entityManager as PersistentEntitySectionManagerAccessor
    val loadStatus = accessor.bingoChunkLoadStatuses[chunkLong]?.toString() ?: ENTITY_CHUNK_LOAD_STATUS_FRESH
    val visibility = accessor.bingoChunkVisibility[chunkLong]?.toString() ?: Visibility.HIDDEN.toString()
    return EntityChunkLifecycleDiagnostics(
        loadStatus = loadStatus,
        visibility = visibility,
        loadingInboxSize = accessor.bingoLoadingInbox.size,
    )
}

internal const val ENTITY_CHUNK_LOAD_STATUS_FRESH = "FRESH"
internal const val ENTITY_CHUNK_LOAD_STATUS_LOADED = "LOADED"
internal const val ENTITY_CHUNK_VISIBILITY_TICKING = "TICKING"

internal fun <T> loadChunkFutureWithRegionTicket(
    addTicket: () -> kotlin.Unit,
    runDistanceManagerUpdates: () -> kotlin.Unit,
    getChunkFuture: () -> CompletableFuture<T>,
    removeTicket: () -> kotlin.Unit,
    taskExecutor: Executor,
): CompletableFuture<T> {
    addTicket()
    return try {
        runDistanceManagerUpdates()
        val chunkFuture = getChunkFuture()
        chunkFuture.whenCompleteAsync({ _, _ -> removeTicket() }, taskExecutor)
        chunkFuture
    } catch (t: Throwable) {
        removeTicket()
        throw t
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
        world.chunkSource.addRegionTicket(TICKET_HELD, ChunkPos(chunk.first, chunk.second), 0, MinecraftUnit.INSTANCE)
        return ChunkTicketHandle(chunk)
    }

    inner class ChunkTicketHandle(
        private val chunk: Pair<Int, Int>
    ) : IServerWorld.IChunkTicketHandle {
        override fun close() {
            world.chunkSource.removeRegionTicket(TICKET_HELD, ChunkPos(chunk.first, chunk.second), 0, MinecraftUnit.INSTANCE)
        }
    }

    override fun getChunkSync(chunk: Pair<Int, Int>): IChunk {
        return ChunkImpl(world.getChunk(chunk.first, chunk.second))
    }

    override fun areChunkEntitiesReady(chunk: Pair<Int, Int>): Boolean {
        val chunkLong = ChunkPos.asLong(chunk.first, chunk.second)
        val diagnostics = collectEntityChunkLifecycleDiagnostics(world, chunkLong)
        // The chunk's persisted entities are live once its entity section is
        // LOADED/TICKING. We previously also required a globally-empty loading
        // inbox, but under entity-load pressure the inbox can stay non-empty
        // indefinitely (observed stuck at 8/705), so that gate never passed and
        // the lobby menu only spawned via the 100-tick timeout fallback — which
        // also left players' entity tracking unsettled (the "others invisible"
        // bug). The per-chunk LOADED+TICKING status already means THIS chunk's
        // entities have drained from the inbox, which is what we actually need.
        return isEntityChunkLifecycleHealthy(diagnostics.loadStatus, diagnostics.visibility)
    }

    override fun getChunkAsync(chunk: Pair<Int, Int>): CompletableFuture<IChunk?> {
        if (!world.server.isSameThread) {
            return CompletableFuture.supplyAsync({ getChunkAsync(chunk) }, world.server)
                .thenCompose({ it })
        }

        val chunkManager = world.chunkSource
        val chunkPos = ChunkPos(chunk.first, chunk.second)
        return loadChunkFutureWithRegionTicket(
            addTicket = {
                chunkManager.addRegionTicket(TICKET_ASYNC, chunkPos, 0, MinecraftUnit.INSTANCE)
            },
            runDistanceManagerUpdates = {
                (chunkManager as ServerChunkManagerAccessor).invokeUpdateChunks()
                kotlin.Unit
            },
            getChunkFuture = {
                chunkManager.getChunkFuture(chunk.first, chunk.second, ChunkStatus.FULL, true)
                    .thenApply { it.orElse(null) }
            },
            removeTicket = {
                chunkManager.removeRegionTicket(TICKET_ASYNC, chunkPos, 0, MinecraftUnit.INSTANCE)
            },
            taskExecutor = taskExecutor,
        ).thenApply { chunkAccess: ChunkAccess? ->
            if (chunkAccess != null) ChunkImpl(chunkAccess) else null
        }
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
