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
import me.jfenn.bingo.platform.event.model.EntityLoadEvent
import me.jfenn.bingo.platform.event.model.TickEvent
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.PlayerHeadBlock
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.chunk.status.ChunkStatus
import org.joml.Matrix4d
import org.joml.Vector3d
import org.joml.Vector4d
import org.slf4j.Logger
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.*
import kotlin.math.atan2
import kotlin.math.roundToInt

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
) : BingoComponent() {

    private var menu: MenuInstance? = null
    private val koinScope = scopeManager.getScope(server)!!

    private val instanceTag = "bingo-${UUID.randomUUID()}"

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
            // this *shouldn't* happen... just being safe to prevent double-entities
            if (menu != null) {
                log.error("BUG: GameState.PREGAME onEnter handler has been invoked already!")
                return@onEnter
            }

            if (!state.isLobbyMode) {
                log.info("[MenuController] Not spawning lobby, as isLobbyMode=false")
                return@onEnter
            }

            val lobbyWorld = server.lobbyWorld
            if (lobbyWorld == null) {
                log.error("[MenuController] Not spawning lobby, as the dimension does not exist")
                return@onEnter
            }

            val (lobbySpawnPos, lobbySpawnYaw) = getLobbySpawnPosition(lobbyWorld)
            state.lobbySpawnPos = lobbySpawnPos
            state.lobbySpawnYaw = lobbySpawnYaw

            val menuPos = getMenuPosition(lobbyWorld)
            if (menuPos != null) {
                log.info("[MenuController] Spawning lobby menu at ${menuPos.getTranslation(Vector3d())}")
                val menuInstance = MenuInstance(
                    log = log,
                    koinScope = koinScope,
                    world = lobbyWorld,
                    entityManager = entityManager,
                    interactionEntityEvents = interactionEntityEvents,
                    matrix = menuPos,
                    instanceTag = instanceTag,
                )
                menu = menuInstance
            } else {
                log.warn("[MenuController] Not spawning menu entities as the lobby has no menu position.")
            }

            spawnTeamEntities(lobbyWorld)
        }

        eventBus.register(EntityLoadEvent) {
            val lobbyWorld = server.lobbyWorld ?: return@register
            val bingoTag = it.entity.tags.find { tag -> tag.startsWith("bingo-") }
                ?: return@register

            if (it.world == lobbyWorld && it.entity !is Player && bingoTag != instanceTag) {
                log.warn("Discarding an existing lobby world entity: ${it.entity.type}")
                it.entity.discard()
            }
        }

        eventBus.register(TickEvent.Start) {
            menu?.tick()
        }

        events.onStateChange { (_, to) ->
            if (to != GameState.PREGAME) {
                menu?.cleanup()
                menu = null

                // discard team picker entities which aren't part of the menu
                val lobbyWorld = server.lobbyWorld ?: return@onStateChange
                lobbyWorld.allEntities
                    .filter { it !is Player && it.tags.contains(instanceTag) }
                    .forEach { it.discard() }
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

    fun prepareLobbyFiles() = log.measureTime("[MenuController] Preparing the BINGO lobby...") {
        val storageDir = levelStorage.getLevelSaveDir(LOBBY_WORLD_ID)
            ?: run {
                log.error("Unable to find world storage dir for $LOBBY_WORLD_ID")
                return@measureTime
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
                    entryName.startsWith("entities/") ||
                    entryName.startsWith("region/")
                ) {
                    val outPath = storageDir.resolve(entryName)
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
        log.info("Set menu position to ${menuPosition?.transform(Vector4d())}")

        if (menuPosition != null) {
            val blockPos = Vector4d()
                .also { menuPosition.transform(it) }
                .let { BlockPosition(it.x.roundToInt(), it.y.roundToInt(), it.z.roundToInt()) }

            taskExecutor.execute {
                // schedule the blockstate change, as the chunk might not be loaded yet
                for (y in blockPos.y-1..blockPos.y+1) {
                    lobbyWorld.setBlockAndUpdate(
                        blockPos.copy(y = y).toBlockPos(),
                        Blocks.AIR.defaultBlockState(),
                    )
                }
            }
        }
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

        taskExecutor.execute {
            // schedule the blockstate change, as the chunk might not be loaded yet
            lobbyWorld.setBlockAndUpdate(it.first.toBlockPos(), Blocks.AIR.defaultBlockState())
        }
    }

    private fun spawnTeamEntities(lobbyWorld: ServerLevel) {
        val lobbyWorldImpl: IServerWorld = serverWorldFactory.forWorld(lobbyWorld)

        val teamPositions = if (Cache.teamPositionsKnown) {
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

        if (teamPositions.isEmpty()) {
            log.warn("[MenuController] No lobby team positions were found. Team picker entities will not be spawned. Expected colored carpets above lodestone blocks in a 48x48 box around the lobby origin.")
        }

        for ((teamKey, blockPos) in teamPositions) {
            val team = data.teamPresets[teamKey] ?: continue
            spawnTeamEntity(lobbyWorld, Vector3d(blockPos.x + 0.5, blockPos.y.toDouble(), blockPos.z + 0.5), teamKey, team, instanceTag)

            taskExecutor.execute {
                // schedule the blockstate change, as the chunk might not be loaded yet
                lobbyWorld.setBlockAndUpdate(blockPos.toBlockPos(), Blocks.AIR.defaultBlockState())
            }
        }
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
    }

    private fun spawnTeamEntity(
        lobbyWorld: ServerLevel,
        position: Vector3d,
        teamKey: BingoTeamKey,
        teamPreset: BingoTeamPreset,
        instanceTag: String,
    ) {
        lobbyWorld.loadChunkAt(position)

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
