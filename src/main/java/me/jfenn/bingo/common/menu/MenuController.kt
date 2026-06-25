package me.jfenn.bingo.common.menu

import me.jfenn.bingo.common.LOBBY_WORLD_ID
import me.jfenn.bingo.common.Sounds
import me.jfenn.bingo.common.data.ScopedData
import me.jfenn.bingo.common.datapack.LobbyWorldService
import me.jfenn.bingo.common.event.InteractionEntityEvents
import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.lobbyWorld
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.scope.ScopeManager
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.team.BingoTeam
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.common.team.BingoTeamPreset
import me.jfenn.bingo.common.team.TeamService
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.common.utils.measureTime
import me.jfenn.bingo.common.utils.plus
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.*
import me.jfenn.bingo.platform.block.BlockPosition
import me.jfenn.bingo.platform.block.IWallSignBlockState
import me.jfenn.bingo.platform.world.IChunkHeightmap
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.model.TickEvent
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.PlayerHeadBlock
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.TicketType
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.chunk.status.ChunkStatus
import org.joml.Matrix4d
import org.joml.Vector3d
import org.slf4j.Logger
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.*
import kotlin.math.atan2
import kotlin.math.roundToInt

internal data class MenuEntityStats(
    val current: Int = 0,
    val stale: Int = 0,
) {
    val total: Int get() = current + stale

    override fun toString(): String {
        return "total=$total,current=$current,stale=$stale"
    }
}

internal class MenuController(
    events: ScopedEvents,
    eventBus: IEventBus,
    scopeManager: ScopeManager,
    private val log: Logger,
    private val server: MinecraftServer,
    private val entityManager: IEntityManager,
    private val interactionEntityEvents: InteractionEntityEvents,
    private val state: BingoState,
    private val data: ScopedData,
    private val teamService: TeamService,
    private val text: TextProvider,
    private val levelStorage: ILevelStorage,
    private val particleFactory: me.jfenn.bingo.platform.particle.IParticleFactory,
    private val serverWorldFactory: IServerWorldFactory,
    private val taskExecutor: IExecutors.IServerTaskExecutor,
    private val lobbyWorldService: LobbyWorldService,
) : BingoComponent(), RuntimeLobbyController {

    private var menu: MenuInstance? = null
    private var suppressNextPregameSpawn = false
    private val koinScope = scopeManager.getScope(server)!!
    private val teamPickerChunks = mutableSetOf<ChunkPos>()

    // Unique tag for entities belonging to the current lobby instance. Regenerated each time
    // the lobby is spawned so stale runtime menu/team entities can be discarded safely.
    private var instanceTag = "bingo-${UUID.randomUUID()}"

    private companion object {
        val TEAM_PICKER_TICKET: TicketType<ChunkPos> = TicketType.create("bingo-team-picker") { a, b ->
            a.toLong().compareTo(b.toLong())
        }
    }

    private object Cache {
        var lobbyWorldModified: Instant = Instant.MIN
        var spawnPosition: Pair<BlockPosition, Float>? = null
        var menuPosition: Matrix4d? = null
        var teamPositions: Map<BingoTeamKey, BlockPosition>? = null
        var menuPositionKnown: Boolean = false
        var teamPositionsKnown: Boolean = false
        var spawnPositionKnown: Boolean = false
    }

    init {
        if (state.isLobbyMode) {
            prepareLobbyFiles()
        }

        events.onEnter(GameState.PREGAME)  {
            val lobbyWorld = server.lobbyWorld
            if (lobbyWorld == null) {
                log.error("[MenuController] Not spawning lobby, as the dimension does not exist")
                return@onEnter
            }

            refreshLobbyState(lobbyWorld)
            if (suppressNextPregameSpawn) {
                suppressNextPregameSpawn = false
                return@onEnter
            }

            spawnLobby(lobbyWorld)
        }

        eventBus.register(TickEvent.Start) {
            menu?.tick()
        }

        events.onStateChange { (_, to) ->
            if (to != GameState.PREGAME) {
                suppressNextPregameSpawn = false
                cleanupLobby(server.lobbyWorld)
            }
        }

        events.onChangeOptions {
            menu?.markDirty()
        }

        events.onPlayerJoin {
            menu?.markDirty()
        }

        events.onPlayerDisconnect {
            menu?.markDirty()
        }
    }

    override fun suspendPregameSpawn() {
        suppressNextPregameSpawn = true
    }

    override fun menuEntityStats(): MenuEntityStats {
        val lobbyWorld = server.lobbyWorld ?: return MenuEntityStats()
        return menuEntityStats(lobbyWorld)
    }

    fun menuEntityCount(): Int {
        return menuEntityStats().total
    }

    private fun menuEntityStats(lobbyWorld: ServerLevel): MenuEntityStats {
        var current = 0
        var stale = 0
        lobbyWorld.allEntities.forEach { entity ->
            if (entity is Player) return@forEach
            val bingoTags = entity.tags.filter { it.startsWith("bingo-") }
            if (bingoTags.isEmpty()) return@forEach
            if (instanceTag in bingoTags) current++ else stale++
        }
        return MenuEntityStats(current = current, stale = stale)
    }

    override fun spawnLobby() {
        spawnLobby(server.lobbyWorld)
    }

    fun spawnLobby(lobbyWorld: ServerLevel?) {
        if (!state.isLobbyMode) {
            log.info("[MenuController] Not spawning lobby, as isLobbyMode=false")
            return
        }

        if (lobbyWorld == null) {
            log.error("[MenuController] Not spawning lobby, as the dimension does not exist")
            return
        }

        if (menu != null) {
            menu?.markDirty()
            return
        }

        val menuPos = refreshLobbyState(lobbyWorld)
        val beforeCleanupStats = menuEntityStats(lobbyWorld)
        cleanupLobby(lobbyWorld)
        val afterCleanupStats = menuEntityStats(lobbyWorld)
        if (afterCleanupStats.total > 0) {
            log.error(
                "[MenuController] Lobby cleanup left bingo entities behind: before={}, after={}",
                beforeCleanupStats,
                afterCleanupStats,
            )
        }

        // Regenerate our instance tag so any menu/team-picker entities that survived from a
        // previous lobby instance now carry an old tag and can be discarded.
        instanceTag = "bingo-${UUID.randomUUID()}"

        if (menuPos != null) {
            log.info("[MenuController] Spawning lobby menu at ${menuPos.getTranslation(Vector3d())}")
            menu = MenuInstance(
                log = log,
                koinScope = koinScope,
                world = lobbyWorld,
                entityManager = entityManager,
                interactionEntityEvents = interactionEntityEvents,
                matrix = menuPos,
                instanceTag = instanceTag,
            )
        } else {
            log.warn("[MenuController] Not spawning menu entities as the lobby has no menu position.")
        }

        spawnTeamEntities(lobbyWorld)
        val afterSpawnStats = menuEntityStats(lobbyWorld)
        if (afterSpawnStats.stale > 0) {
            log.error("[MenuController] Lobby spawned with stale bingo entities still present: {}", afterSpawnStats)
        } else {
            log.info("[MenuController] Lobby entity stats after spawn: {}", afterSpawnStats)
        }
    }

    private fun refreshLobbyState(lobbyWorld: ServerLevel): Matrix4d? {
        val (lobbySpawnPos, lobbySpawnYaw) = getLobbySpawnPosition(lobbyWorld)
        state.lobbySpawnPos = lobbySpawnPos
        state.lobbySpawnYaw = lobbySpawnYaw
        val menuPosition = getMenuPosition(lobbyWorld)
        clearLobbyMarkerBlocks(lobbyWorld, lobbySpawnPos, menuPosition, getTeamPositions(lobbyWorld))
        return menuPosition
    }

    private fun cleanupLobby(lobbyWorld: ServerLevel?) {
        menu?.cleanup()
        menu = null

        if (lobbyWorld == null) {
            teamPickerChunks.clear()
            return
        }

        releaseTeamPickerTickets(lobbyWorld)
        lobbyWorld.allEntities
            .filter { it !is Player && it.tags.any { tag -> tag.startsWith("bingo-") } }
            .forEach(::discardLobbyEntity)
    }

    private fun discardLobbyEntity(entity: Entity) {
        interactionEntityEvents.removeInteract(entity.uuid)
        entity.discard()
    }

    override fun prepareLobbyFiles() = log.measureTime("[MenuController] Preparing the BINGO lobby...") {
        val storageDir = levelStorage.getLevelSaveDir(LOBBY_WORLD_ID)
            ?.normalize()
            ?: run {
                log.error("Unable to find world storage dir for $LOBBY_WORLD_ID")
                return@measureTime
            }

        // Lobby menu/team picker entities are generated at runtime. Persisted entities from the
        // bundled lobby world keep fixed UUIDs and collide with the freshly spawned menu.
        val entitiesDir = storageDir.resolve("entities").normalize()
        if (!entitiesDir.startsWith(storageDir)) {
            log.error("[MenuController] Tried to delete a directory outside of the world location: $entitiesDir")
        } else {
            entitiesDir.toFile().deleteRecursively()
        }

        // If the lobby has been modified, clear cached positions
        val lobbyWorldModified = lobbyWorldService.readLastModified()
        if (lobbyWorldModified.isAfter(Cache.lobbyWorldModified)) {
            Cache.spawnPosition = null
            Cache.menuPosition = null
            Cache.teamPositions = null
            Cache.spawnPositionKnown = false
            Cache.menuPositionKnown = false
            Cache.teamPositionsKnown = false
            Cache.lobbyWorldModified = lobbyWorldModified
        }

        lobbyWorldService.openLobbyZip().use { zipStream ->
            while (true) {
                val entry = zipStream.nextEntry ?: break
                if (entry.isDirectory) continue

                val entryName = entry.name.substringAfter('/')

                if (
                    entryName.startsWith("region/")
                ) {
                    val outPath = storageDir.resolve(entryName).normalize()
                    if (!outPath.startsWith(storageDir)) {
                        log.error("[MenuController] Tried to create a file outside of the world location: $outPath")
                        continue
                    }

                    Files.createDirectories(outPath.parent)

                    log.debug("Copying file -> {}", outPath)
                    try {
                        Files.copy(zipStream, outPath, StandardCopyOption.REPLACE_EXISTING)
                    } catch (e: IOException) {
                        log.error("[MenuController] Error copying lobby file $entryName", e)
                    }
                }

                zipStream.closeEntry()
            }
        }
    }

    private fun searchLobbyBlocks() = sequence {
        val lobbyWorld = server.lobbyWorld ?: return@sequence
        val lobbyWorldImpl = serverWorldFactory.forWorld(lobbyWorld)
        val bottomY = lobbyWorld.minBuildHeight
        val chunkSearchOrder = listOf(0, 1, -1, 2, -2)

        for (chunkX in chunkSearchOrder) {
            for (chunkZ in chunkSearchOrder) {
                val chunk = lobbyWorldImpl.getChunkSync(chunkX to chunkZ)
                val heightmap = chunk.getHeightmap(IChunkHeightmap.Type.MOTION_BLOCKING_NO_LEAVES)
                for (localX in 0 until 16) {
                    for (localZ in 0 until 16) {
                        val height = heightmap.get(localX, localZ) + 3
                        for (y in height downTo bottomY) {
                            yield(BlockPosition(chunkX * 16 + localX, y, chunkZ * 16 + localZ))
                        }
                    }
                }
            }
        }
    }

    private fun getMenuPosition(lobbyWorld: ServerLevel): Matrix4d? = run {
        if (Cache.menuPositionKnown) {
            return@run Cache.menuPosition
        }

        log.measureTime("[MenuController] Locating menu position...") {
            val lobbyWorldImpl = serverWorldFactory.forWorld(lobbyWorld)

            // Find a column of three vertical wall_sign blocks for the menu position
            for (pos in searchLobbyBlocks()) {
                val blocks = listOf(
                    pos.move(y = 1).toBlockPos(),
                    pos.toBlockPos(),
                    pos.move(y = -1).toBlockPos()
                ).associateWith {
                    lobbyWorldImpl.getBlockState(BlockPosition.fromBlockPos(it))
                            as? IWallSignBlockState
                }

                val isMenuPos = blocks.values.all {
                    it?.identifier == "minecraft:oak_wall_sign"
                }
                val centerState = blocks.values.first()

                if (isMenuPos && centerState != null) {
                    log.info("[MenuController] Found lobby menu wall signs at {}", pos)
                    val directionVec = centerState.facing
                    val yaw = atan2(directionVec.x.toDouble(), directionVec.z.toDouble())

                    val matrix4d = Matrix4d()
                    matrix4d.translate(pos.toVector3f())
                    matrix4d.rotateY(yaw)

                    return@measureTime matrix4d
                }
            }

            null
        }
    }.also { menuPosition ->
        Cache.menuPosition = menuPosition
        Cache.menuPositionKnown = true
        log.info("Set menu position to ${menuPosition?.getTranslation(Vector3d())}")
    }

    private fun getLobbySpawnPosition(lobbyWorld: ServerLevel): Pair<BlockPosition, Float> = run {
        if (Cache.spawnPositionKnown) {
            return@run Cache.spawnPosition ?: (BlockPosition(0, lobbyWorld.minBuildHeight, 0) to 180f)
        }

        log.measureTime("[MenuController] Locating spawn position...") {
            // If a player head is in the lobby structure, use it as the player spawn
            for (pos in searchLobbyBlocks()) {
                val blockState = lobbyWorld.getBlockState(pos.toBlockPos())
                val isPlayerHead = blockState.block == Blocks.PLAYER_HEAD
                if (isPlayerHead) {
                    log.info("[MenuController] Found lobby spawn head at {}", pos)
                    val rotation = blockState.getValue(PlayerHeadBlock.ROTATION)
                    val yaw = (rotation - 8).toFloat() * 360f / 16f
                    return@measureTime Pair(pos, yaw)
                }
            }

            // otherwise, find the highest block at 0,0
            val chunk = serverWorldFactory.forWorld(lobbyWorld).getChunkSync(0 to 0)
            val spawnY = chunk.getHeightmap(IChunkHeightmap.Type.MOTION_BLOCKING_NO_LEAVES).get(0, 0)

            log.info("[MenuController] Falling back to world origin spawn at 0,{},0", spawnY)
            BlockPosition(0, spawnY, 0) to 180f
        }
    }.also {
        Cache.spawnPosition = it
        Cache.spawnPositionKnown = true
        log.info("Set lobby spawnpoint to $it")
    }

    private fun getTeamPositions(lobbyWorld: ServerLevel): Map<BingoTeamKey, BlockPosition> {
        val lobbyWorldImpl: IServerWorld = serverWorldFactory.forWorld(lobbyWorld)

        return if (Cache.teamPositionsKnown) {
            Cache.teamPositions ?: emptyMap()
        } else log.measureTime("[MenuController] Locating team entities...") {
            val ret = mutableMapOf<BingoTeamKey, BlockPosition>()
            // Search for team join entities
            for ((x, y, z) in searchLobbyBlocks()) {
                val isLodestone = lobbyWorld.getBlockState(BlockPos(x, y-1, z)).block == Blocks.LODESTONE
                if (!isLodestone) continue

                val blockPos = BlockPosition(x, y, z)
                val blockState = lobbyWorldImpl.getBlockState(blockPos)

                val (teamId, _) = data.teamPresets.entries
                    .find { it.value.blockId == blockState.identifier }
                    ?: continue

                ret[teamId] = blockPos
                log.info("[MenuController] Found lobby team picker for {} at {}", teamId.id, blockPos)
            }

            ret
        }.also {
            Cache.teamPositions = it
            Cache.teamPositionsKnown = true
        }
    }

    private fun spawnTeamEntities(lobbyWorld: ServerLevel) {
        val teamPositions = getTeamPositions(lobbyWorld)

        if (teamPositions.isEmpty()) {
            log.warn("[MenuController] No lobby team positions were found. Team picker entities will not be spawned. Expected colored carpets above lodestone blocks in a 48x48 box around the lobby origin.")
        }

        for ((teamKey, blockPos) in teamPositions) {
            val team = data.teamPresets[teamKey] ?: continue
            clearMarkerBlock(lobbyWorld, blockPos)
            spawnTeamEntity(lobbyWorld, Vector3d(blockPos.x + 0.5, blockPos.y.toDouble(), blockPos.z + 0.5), teamKey, team, instanceTag)
        }
    }

    private fun clearLobbyMarkerBlocks(
        lobbyWorld: ServerLevel,
        spawnPosition: BlockPosition,
        menuPosition: Matrix4d?,
        teamPositions: Map<BingoTeamKey, BlockPosition>,
    ) {
        clearSpawnMarkerBlock(lobbyWorld, spawnPosition)
        clearMenuMarkerBlocks(lobbyWorld, menuPosition)
        clearTeamMarkerBlocks(lobbyWorld, teamPositions)
    }

    private fun clearSpawnMarkerBlock(lobbyWorld: ServerLevel, spawnPosition: BlockPosition) {
        clearMarkerBlock(lobbyWorld, spawnPosition)
    }

    private fun clearMenuMarkerBlocks(lobbyWorld: ServerLevel, menuPosition: Matrix4d?) {
        val blockPos = menuPosition
            ?.getTranslation(Vector3d())
            ?.let { BlockPosition(it.x.roundToInt(), it.y.roundToInt(), it.z.roundToInt()) }
            ?: return

        for (y in blockPos.y - 1..blockPos.y + 1) {
            clearMarkerBlock(lobbyWorld, blockPos.copy(y = y))
        }
    }

    private fun clearTeamMarkerBlocks(lobbyWorld: ServerLevel, teamPositions: Map<BingoTeamKey, BlockPosition>) {
        for (blockPos in teamPositions.values) {
            clearMarkerBlock(lobbyWorld, blockPos)
        }
    }

    private fun clearMarkerBlock(lobbyWorld: ServerLevel, blockPos: BlockPosition) {
        lobbyWorld.getChunk(Math.floorDiv(blockPos.x, 16), Math.floorDiv(blockPos.z, 16), ChunkStatus.FULL)
        lobbyWorld.setBlockAndUpdate(blockPos.toBlockPos(), Blocks.AIR.defaultBlockState())
    }

    private fun ServerLevel.loadChunkAt(position: Vector3d) {
        val chunkPos = ChunkPos(
            BlockPos(
                kotlin.math.floor(position.x).toInt(),
                0,
                kotlin.math.floor(position.z).toInt(),
            )
        )
        getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FULL)
        chunkSource.addRegionTicket(TEAM_PICKER_TICKET, chunkPos, 0, chunkPos)
        teamPickerChunks += chunkPos
    }

    private fun releaseTeamPickerTickets(lobbyWorld: ServerLevel) {
        for (chunkPos in teamPickerChunks) {
            lobbyWorld.chunkSource.removeRegionTicket(TEAM_PICKER_TICKET, chunkPos, 0, chunkPos)
        }
        teamPickerChunks.clear()
    }

    private fun Vector3d.isNear(other: Vector3d): Boolean {
        return distanceSquared(other) < 1.0E-4
    }

    private fun isLobbyEntityType(type: EntityType<*>): Boolean {
        return type == EntityType.TEXT_DISPLAY ||
                type == EntityType.BLOCK_DISPLAY ||
                type == EntityType.INTERACTION
    }

    private fun cleanupTeamPickerEntities(lobbyWorld: ServerLevel, position: Vector3d) {
        val positions = listOf(
            position + Vector3d(0.0, 1.5, 0.0),
            position + Vector3d(0.0, 1.0, 0.0),
            position + Vector3d(0.0, -1.0, 0.0),
        )

        entityManager.iterateEntities(lobbyWorld)
            .filter { isLobbyEntityType(it.type) }
            .filter { entity -> positions.any { it.isNear(entity.pos) } }
            .filter {
                val bingoTags = it.commandTags.filter { tag -> tag.startsWith("bingo-") }
                bingoTags.isEmpty() || instanceTag !in bingoTags
            }
            .forEach { entity ->
                interactionEntityEvents.removeInteract(entity.uuid)
                entity.discard()
            }
    }

    private fun spawnTeamEntity(
        lobbyWorld: ServerLevel,
        position: Vector3d,
        teamKey: BingoTeamKey,
        teamPreset: BingoTeamPreset,
        instanceTag: String,
    ) {
        lobbyWorld.loadChunkAt(position)
        cleanupTeamPickerEntities(lobbyWorld, position)

        val nameEntity = entityManager.createEntity(EntityType.TEXT_DISPLAY, lobbyWorld)
            .apply {
                pos = position + Vector3d(0.0, 1.5, 0.0)
                value = BingoTeam.fromPreset(teamKey, teamPreset)
                    .getName(
                        textProvider = text,
                        symbol = true,
                    )
                billboard = ITextDisplayEntity.Billboard.VERTICAL
                alignment = ITextDisplayEntity.TextAlignment.CENTER
                background = 0
                shadow = true
            }
            .also { it.commandTags += instanceTag }
            .also {
                if (!entityManager.spawnEntity(lobbyWorld, it)) {
                    log.warn("[MenuController] Unable to spawn team name entity for ${teamKey.id} at ${it.pos}")
                    it.discard()
                    return
                }
            }

        val hintEntity = entityManager.createEntity(EntityType.TEXT_DISPLAY, lobbyWorld)
            .apply {
                pos = position + Vector3d(0.0, 1.0, 0.0)
                value = text.string(StringKey.LobbyClickToJoin)
                billboard = ITextDisplayEntity.Billboard.VERTICAL
                alignment = ITextDisplayEntity.TextAlignment.CENTER
            }
            .also { it.commandTags += instanceTag }
            .also {
                if (!entityManager.spawnEntity(lobbyWorld, it)) {
                    log.warn("[MenuController] Unable to spawn team hint entity for ${teamKey.id} at ${it.pos}")
                    it.discard()
                    nameEntity.discard()
                    return
                }
            }

        val interactionListenerEntity = entityManager.createEntity(EntityType.INTERACTION, lobbyWorld)
            .apply {
                pos = position + Vector3d(0.0, -1.0, 0.0)
                width = 2f
                height = 4f
            }
            .also { it.commandTags += instanceTag }
            .also {
                if (!entityManager.spawnEntity(lobbyWorld, it)) {
                    log.warn("[MenuController] Unable to spawn team interaction entity for ${teamKey.id} at ${it.pos}")
                    it.discard()
                    nameEntity.discard()
                    hintEntity.discard()
                    return
                }
            }

        log.info("[MenuController] Spawned lobby team picker for {} at {}", teamKey.id, position)

        interactionEntityEvents.onInteract(interactionListenerEntity) { player ->
            val team = BingoTeam.fromPreset(teamKey, teamPreset)

            particleFactory.createDustParticle(team.textColor.color ?: 0, 1f)
                .spawn(player, position, 10, Vector3d(1.0), 0.2)

            Sounds.playTeamChanged(player)
            teamService.joinTeam(player, team)
        }
    }

}
