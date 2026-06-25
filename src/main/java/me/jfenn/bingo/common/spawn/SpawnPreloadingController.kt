package me.jfenn.bingo.common.spawn

import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.event.packet.ServerPacketEvents
import me.jfenn.bingo.common.ready.ReadyUpdatePacket
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.common.utils.minus
import me.jfenn.bingo.common.utils.seconds
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.IExecutors
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.event.IEventBus
import org.slf4j.Logger
import java.time.Instant
import java.util.concurrent.CompletableFuture
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min

/**
 * Wait until chunks around spawn are generated
 */
internal class SpawnPreloadingController(
    events: ScopedEvents,
    eventBus: IEventBus,
    private val state: BingoState,
    private val config: BingoConfig,
    private val spawnService: SpawnService,
    private val serverTaskExecutor: IExecutors.IServerTaskExecutor,
    private val playerManager: IPlayerManager,
    private val packet: ServerPacketEvents,
    private val text: TextProvider,
    private val log: Logger,
) : BingoComponent() {

    private var chunkFutureList: List<CompletableFuture<*>> = emptyList()

    private fun isWithinDistance(dx: Int, dz: Int, distance: Int): Boolean {
        val deltaX = max(0, dx.absoluteValue - 1)
        val deltaZ = max(0, dz.absoluteValue - 1)
        val max = (max(deltaX, deltaZ) - 1).toLong().coerceAtLeast(0L)
        val min = min(deltaX, deltaZ).toLong()
        val distanceSquared = min * min + max * max
        return distanceSquared < (distance * distance).toLong()
    }

    init {
        events.onEnter(GameState.PRELOADING) {
            val startedLoading = Instant.now()

            val world = spawnService.getSpawnDimension()
            val viewDistance = config.server.preloadViewDistance
            val chunkOffsets = buildList {
                for (x in -viewDistance..viewDistance) for (z in -viewDistance..viewDistance) {
                    if (isWithinDistance(x, z, viewDistance)) {
                        add(Pair(x, z))
                    }
                }
            }

            val chunks = state.getRegisteredTeams()
                .mapNotNull { it.spawnpoint }
                .flatMap { (x, z) ->
                    chunkOffsets
                        .map { (offsetX, offsetZ) ->
                            Pair(x + offsetX, z + offsetZ)
                        }
                }
                .toSet()

            log.info("[SpawnPreloadingController] Pre-generating ${chunks.size} chunks within a view distance of ${viewDistance}...")
            val futures = chunks.map { chunk ->
                world.getChunkAsync(chunk)
                    .whenComplete { _, _ ->
                        log.debug(
                            "[SpawnPreloadingController] Finished chunk {} in {}",
                            chunk,
                            Instant.now() - startedLoading
                        )
                    }
            }

            chunkFutureList = futures
            CompletableFuture.allOf(*futures.toTypedArray())
                .whenCompleteAsync({ _, _ ->
                    log.info("[SpawnPreloadingController] Finished preloading spawn chunks in ${Instant.now() - startedLoading}")
                    chunkFutureList = emptyList()
                    state.changeState(eventBus, GameState.LOADING)
                }, serverTaskExecutor)
        }

        events.onGameTick {
            when (state.state) {
                // Show an empty (0%) progress bar as soon as the "Bingo is starting" title
                // appears, so the loading bar is visible from the start of STARTING rather than
                // only flashing during PRELOADING. The spawnpoints are still being computed
                // asynchronously here, so the exact progress is not yet known.
                GameState.STARTING -> sendLoadingProgress(doneCount = 0, totalCount = 0)
                GameState.PRELOADING -> {
                    if (chunkFutureList.isEmpty()) return@onGameTick
                    sendLoadingProgress(
                        doneCount = chunkFutureList.count { it.isDone },
                        totalCount = chunkFutureList.size,
                    )
                }
                else -> return@onGameTick
            }
        }
    }

    /**
     * Broadcast the terrain-loading progress bar to all players.
     *
     * When [totalCount] is 0 the progress is unknown (e.g. during STARTING, while spawnpoints
     * are still being computed), so an empty bar is shown without a percentage subtitle.
     */
    private fun sendLoadingProgress(doneCount: Int, totalCount: Int) {
        val hasProgress = totalCount > 0
        val percentage = if (hasProgress) {
            doneCount.times(100f).div(totalCount).let { String.format("%.0f", it) + "%" }
        } else null

        val message = text.empty()
            .append(text.string(StringKey.LobbyLoadingTerrain))
            .let { if (percentage != null) it.append(" $percentage ($doneCount/$totalCount)") else it }

        val updatePacket = ReadyUpdatePacket(
            isRunning = true,
            isReady = true,
            state = state.state,
            // remainingDuration only drives the progress bar, so this does not need to be accurate.
            // When progress is unknown, keep remaining == total so the bar renders empty (0%).
            remainingDuration = if (hasProgress) (totalCount - doneCount).seconds else 1.seconds,
            totalDuration = if (hasProgress) totalCount.seconds else 1.seconds,
            readyPlayers = 0,
            totalPlayers = 0,
            title = text.string(StringKey.LobbyLoadingTerrain),
            // Use an empty (non-null) subtitle when progress is unknown, so the client does not
            // fall back to rendering a misleading "time remaining" countdown.
            subtitle = percentage?.let { text.literal("$it ($doneCount/$totalCount)") } ?: text.empty(),
            canSendReady = false,
        )

        for (player in playerManager.getPlayers()) {
            when {
                packet.readyUpdateV3.send(player, updatePacket) -> {}
                packet.readyUpdateV2.send(player, updatePacket) -> {}
                // readyUpdateV1 is intentionally omitted, as it does not support GameState.PRELOADING or custom titles
                else -> player.sendHotbarMessage(message)
            }
        }
    }
}