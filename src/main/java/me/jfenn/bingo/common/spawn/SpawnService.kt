package me.jfenn.bingo.common.spawn

import me.jfenn.bingo.common.LOBBY_WORLD_IDENTIFIER
import me.jfenn.bingo.common.NBT_BINGO_IGNORE
import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.options.BingoOptions
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.team.BingoTeam
import me.jfenn.bingo.common.team.TeamService
import me.jfenn.bingo.impl.collectEntityChunkLifecycleDiagnostics
import me.jfenn.bingo.impl.isEntityChunkLifecycleHealthy
import me.jfenn.bingo.platform.IExecutors
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IServerWorld
import me.jfenn.bingo.platform.IServerWorldFactory
import me.jfenn.bingo.platform.block.BlockPosition
import net.minecraft.world.level.ChunkPos
import org.slf4j.Logger
import java.util.concurrent.CompletableFuture
import java.util.function.Function
import kotlin.math.absoluteValue
import kotlin.math.sign
import kotlin.random.Random

internal class SpawnService(
    private val log: Logger,
    private val options: BingoOptions,
    private val state: BingoState,
    private val config: BingoConfig,
    private val teamService: TeamService,
    private val spreadPlayersFactory: SpreadPlayers.Factory,
    private val serverWorldFactory: IServerWorldFactory,
    private val serverTaskExecutor: IExecutors.IServerTaskExecutor,
    private val spawnKitService: SpawnKitService,
) {

    fun getSpawnDimension(): IServerWorld {
        val worlds = serverWorldFactory.listWorlds()
        return worlds.find {
            it.identifier == options.spawnDimension
        } ?: run {
            log.error("[SpawnService] Could not find spawnDimension '${options.spawnDimension}'; defaulting to minecraft:overworld")
            serverWorldFactory.overworld
        }
    }

    init {
        // Add load tickets for team spawn chunks on startup
        for (team in state.getRegisteredTeams()) {
            team.spawnpoint?.let { spawnPos ->
                onSpawnpointUpdated(team, spawnPos)
            }
        }
    }

    private fun onSpawnpointUpdated(team: BingoTeam, spawnPos: BlockPosition) {
        // If a team already has this spawnpoint & a ticket, there's nothing else to do
        if (team.spawnpoint == spawnPos && team.spawnpointTicket != null)
            return

        // Otherwise, remove the completed future & add a new spawn ticket
        team.spawnpointFuture = null
        team.spawnpointTicket?.close()

        val worldImpl = getSpawnDimension()
        val newTicket = worldImpl.addTicket(spawnPos.toChunkPos())
        team.spawnpointTicket = newTicket
        team.spawnpoint = spawnPos
    }

    /**
     * Create team spawnpoints
     */
    fun createSpawnpoints(): CompletableFuture<Void> {
        val world = getSpawnDimension()
        val spread = spreadPlayersFactory.forWorld(world, state.gameId)

        val distance = options.spawnDistance
        return if (distance <= 0) {
            // if the distance is 0, find one spawn point for all teams
            val future = spread.findSpawnPosAsync(Pair(0, 0))

            for (team in state.getRegisteredTeams()) {
                team.spawnpointFuture = future
                future.whenCompleteAsync({ spawnPos, _ ->
                    onSpawnpointUpdated(team, spawnPos)
                }, serverTaskExecutor)
            }

            future.thenRun {}
        } else {
            // spread player teams to spawnpoint
            spread.spreadAsync(state.getRegisteredTeams(), distance)
                .whenCompleteAsync ({ map, throwable ->
                    for ((key, spawnPos) in map) {
                        val team = state.teams[key] ?: continue
                        onSpawnpointUpdated(team, spawnPos)
                    }
                }, serverTaskExecutor)
                .thenRun {}
        }
    }

    fun teleportToLobby(player: IPlayerHandle) {
        teleportToLobby(player, force = false)
    }

    fun forceTeleportToLobby(player: IPlayerHandle) {
        teleportToLobby(player, force = true)
    }

    private fun teleportToLobby(player: IPlayerHandle, force: Boolean) {
        val lobbyWorld = serverWorldFactory.listWorlds()
            .find { it.identifier == LOBBY_WORLD_IDENTIFIER }

        if (lobbyWorld == null) {
            log.error("[SpawnService] Attempted teleportToLobby, but the lobby world is null!")
            return
        }

        val spawn = state.lobbySpawnPos
        if (force) {
            player.forceTeleport(lobbyWorld, spawn.toVector3d(), state.lobbySpawnYaw, 0f)
        } else {
            player.teleport(lobbyWorld, spawn.toVector3d(), state.lobbySpawnYaw, 0f)
        }
        player.setSpawnPoint(
            world = lobbyWorld,
            spawn = spawn,
            angle = state.lobbySpawnYaw,
            forced = true,
            sendMessage = false,
        )
        logPlayerAttachmentAfterTeleport(
            stage = if (force) "forceTeleportToLobby" else "teleportToLobby",
            player = player,
            expectedWorld = lobbyWorld,
            force = force,
        )
    }

    fun teleportPlayer(player: IPlayerHandle) {
        if (!state.isLobbyMode) {
            log.error("[SpawnService] Attempted teleportPlayer, but isLobbyMode=false!")
            return
        }

        val world = getSpawnDimension()

        val team = teamService.getPlayerTeam(player)
        val spawn = team?.let {
            getTeamSpawnpoint(team)
        } ?: world.spawnPos

        // Always write the respawn point first — independent of whether a teleport is needed.
        // teleportPlayer is called multiple times during startup (LOADING -> COUNTDOWN -> PLAYING),
        // and on repeated calls the player is usually already at the spawn block (the teleport
        // offset is fractional, so blockPos still equals spawn), so needsTeleport is false. If
        // setSpawnPoint sat after the early-return, it would be skipped, leaving the player's
        // respawnDimension at vanilla's default (overworld) — causing deaths to respawn in the
        // overworld instead of the chosen spawn dimension.
        player.setSpawnPoint(
            world = world,
            spawn = spawn,
            angle = 0f,
            forced = true,
            sendMessage = false,
        )

        // if the player already has the exact block position, don't teleport them again
        val needsTeleport = player.serverWorld != world || player.blockPos != spawn
        if (!needsTeleport) return

        // random offset for each player to prevent collision grouping
        player.teleport(world, spawn.toVector3d().add(Random.nextDouble(), Random.nextDouble(), Random.nextDouble()), player.yaw, player.pitch)
        logPlayerAttachmentAfterTeleport(
            stage = "teleportPlayer",
            player = player,
            expectedWorld = world,
            force = false,
        )
    }

    private fun logPlayerAttachmentAfterTeleport(
        stage: String,
        player: IPlayerHandle,
        expectedWorld: IServerWorld,
        force: Boolean,
    ) {
        val playerWorld = player.serverWorld
        val playerEntity = player.player
        val currentWorld = serverWorldFactory.listWorlds()
            .firstOrNull { it.world.dimension() == playerWorld.dimension() }
            ?.world
        val attached = currentWorld === playerWorld
        val expectedAttached = expectedWorld.world === playerWorld
        val currentWorldId = currentWorld?.let { System.identityHashCode(it).toString() } ?: "null"
        val blockPos = playerEntity.blockPosition()
        val chunkPos = playerEntity.chunkPosition()
        val chunkLong = ChunkPos.asLong(blockPos)
        val distanceManager = playerWorld.chunkSource.chunkMap.getDistanceManager()
        val inLevelPlayers = playerWorld.players().contains(playerEntity)
        val entityTracked = playerWorld.getEntity(playerEntity.uuid) === playerEntity
        val entitiesLoaded = playerWorld.areEntitiesLoaded(chunkLong)
        val entityTicking = playerWorld.isPositionEntityTicking(blockPos)
        val blockTicking = playerWorld.shouldTickBlocksAt(chunkLong)
        val distanceEntityTicking = distanceManager.inEntityTickingRange(chunkLong)
        val distanceBlockTicking = distanceManager.inBlockTickingRange(chunkLong)
        val chunkMapWatching = playerWorld.chunkSource.chunkMap.getPlayers(chunkPos, false).contains(playerEntity)
        val entityLifecycle = collectEntityChunkLifecycleDiagnostics(playerWorld, chunkLong)
        val message = "[ResetDiag] [SpawnService] {}: player={}, dimension={}, playerLevel={}, currentLevel={}, expectedLevel={}, attached={}, expectedAttached={}, inLevelPlayers={}, entityTracked={}, entitiesLoaded={}, entityTicking={}, blockTicking={}, distanceEntityTicking={}, distanceBlockTicking={}, chunkMapWatching={}, entityLoadStatus={}, entityVisibility={}, entityLoadingInboxSize={}, gameMode={}, blockPos={}, force={}"
        if (attached && expectedAttached && inLevelPlayers && entityTracked && entitiesLoaded && entityTicking && blockTicking && distanceEntityTicking && distanceBlockTicking && chunkMapWatching && isEntityChunkLifecycleHealthy(entityLifecycle.loadStatus, entityLifecycle.visibility)) {
            log.info(
                message,
                stage,
                player.playerName,
                playerWorld.dimension().location(),
                System.identityHashCode(playerWorld),
                currentWorldId,
                System.identityHashCode(expectedWorld.world),
                attached,
                expectedAttached,
                inLevelPlayers,
                entityTracked,
                entitiesLoaded,
                entityTicking,
                blockTicking,
                distanceEntityTicking,
                distanceBlockTicking,
                chunkMapWatching,
                entityLifecycle.loadStatus,
                entityLifecycle.visibility,
                entityLifecycle.loadingInboxSize,
                player.gameMode,
                player.blockPos,
                force,
            )
        } else {
            if (distanceEntityTicking && distanceBlockTicking && (!entitiesLoaded || !entityTicking)) {
                log.error(
                    "[ResetDiag] ERROR entity manager chunk lifecycle not ready after {}: player={} chunk=({}, {}) entityLoadStatus={} entityVisibility={} loadingInboxSize={}",
                    stage,
                    player.playerName,
                    chunkPos.x,
                    chunkPos.z,
                    entityLifecycle.loadStatus,
                    entityLifecycle.visibility,
                    entityLifecycle.loadingInboxSize,
                )
            }
            log.error(
                message,
                stage,
                player.playerName,
                playerWorld.dimension().location(),
                System.identityHashCode(playerWorld),
                currentWorldId,
                System.identityHashCode(expectedWorld.world),
                attached,
                expectedAttached,
                inLevelPlayers,
                entityTracked,
                entitiesLoaded,
                entityTicking,
                blockTicking,
                distanceEntityTicking,
                distanceBlockTicking,
                chunkMapWatching,
                entityLifecycle.loadStatus,
                entityLifecycle.visibility,
                entityLifecycle.loadingInboxSize,
                player.gameMode,
                player.blockPos,
                force,
            )
        }
    }

    sealed class GetSpawnpointResult {
        class Found(val position: BlockPosition) : GetSpawnpointResult()
        class SearchChunk(val startChunk: Pair<Int, Int>) : GetSpawnpointResult()
    }
    private fun getTeamSpawnpointInternal(team: BingoTeam): GetSpawnpointResult {
        val world = getSpawnDimension()
        val spawnpoint = team.spawnpoint ?: run {
            log.warn("[SpawnService] Team ${team.id} does not have a stored spawn point! Finding a new spawn position...")

            // Get the chunk of an existing spawnpoint
            val (x, y) = state.getRegisteredTeams()
                .mapNotNull { it.spawnpoint }
                .maxByOrNull { it.x.absoluteValue }
                ?.toChunkPos()
                ?: Pair(0, 0)

            // Move "spreadPlayersDistance" away from the existing chunk on the x-axis
            val xSign = x.sign.takeIf { it != 0 } ?: 1
            val newSpawnChunk = Pair(x + (options.spawnDistance * xSign), y)

            // Search for a spawn
            return GetSpawnpointResult.SearchChunk(newSpawnChunk)
        }

        var pos = spawnpoint
        while (
            !world.getBlockState(pos).isEmpty(world, pos)
            || !world.getBlockState(pos.up()).isEmpty(world, pos.up())
        ) {
            pos = pos.up()
            if (pos.y >= world.world.logicalHeight) {
                log.warn("[SpawnService] Spawn position for team $team is obstructed up to build height! Finding a new spawn position...")
                return GetSpawnpointResult.SearchChunk(spawnpoint.toChunkPos())
            }
        }

        onSpawnpointUpdated(team, pos)
        return GetSpawnpointResult.Found(pos)
    }

    fun getTeamSpawnpointAsync(team: BingoTeam): CompletableFuture<BlockPosition?> {
        if (!state.isLobbyMode) {
            log.error("[SpawnService] Attempted getTeamSpawnpointAsync, but isLobbyMode=false!")
            return CompletableFuture.completedFuture(null)
        }

        val existingFuture = team.spawnpointFuture
        if (existingFuture != null) {
            return existingFuture.thenApply(Function.identity())
        }

        return when (val result = getTeamSpawnpointInternal(team)) {
            is GetSpawnpointResult.SearchChunk -> {
                val world = getSpawnDimension()
                val spread = spreadPlayersFactory.forWorld(world, state.gameId)
                val future = spread.findSpawnPosAsync(result.startChunk)

                team.spawnpointFuture = future
                future.whenComplete({ newSpawnPos, _ ->
                    onSpawnpointUpdated(team, newSpawnPos)
                })
            }
            is GetSpawnpointResult.Found -> CompletableFuture.completedFuture(result.position)
        }
    }

    /**
     * This is only used when spawning/respawning players, and should rarely need to
     * search for a new chunk
     */
    fun getTeamSpawnpoint(team: BingoTeam): BlockPosition? {
        if (!state.isLobbyMode) {
            log.error("[SpawnService] Attempted getTeamSpawnpoint, but isLobbyMode=false!")
            return null
        }

        return when (val result = getTeamSpawnpointInternal(team)) {
            is GetSpawnpointResult.SearchChunk -> {
                val world = getSpawnDimension()
                val spread = spreadPlayersFactory.forWorld(world, state.gameId)
                val spawnPos = spread.findSpawnPos(result.startChunk)

                onSpawnpointUpdated(team, spawnPos)
                spawnPos
            }
            is GetSpawnpointResult.Found -> result.position
        }
    }

    /**
     * Equip the player with any configured spawn equipment
     */
    fun giveSpawnEquipment(players: List<IPlayerHandle>) {
        if (!state.isLobbyMode) {
            log.error("[SpawnService] Attempted giveSpawnEquipment, but isLobbyMode=false!")
            return
        }

        if (!options.isPlayerKit) return

        val itemStacks = spawnKitService.getPlayerItems()

        if (config.preventScoringSpawnKitItems) {
            for (itemStack in itemStacks) {
                // set BINGO_IGNORE flag so that spawn items do not count on the bingo card
                itemStack.addCustomTag(NBT_BINGO_IGNORE)
            }
        }

        for (stack in itemStacks) {
            for (player in players) {
                // copy the stack for each player
                val playerStack = stack.copy()
                // if the item can be equipped (e.g. iron chestplate), put it in an equipment slot
                player.giveOrEquipStack(playerStack)
            }
        }
    }

}
