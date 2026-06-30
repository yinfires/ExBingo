package me.jfenn.bingo.client.integrations.xaero

import me.jfenn.bingo.client.common.event.ClientGameEndEvent
import me.jfenn.bingo.client.platform.event.model.ClientServerEvent
import me.jfenn.bingo.common.LOBBY_WORLD_ID
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.platform.IExecutors
import me.jfenn.bingo.platform.IModEnvironment
import me.jfenn.bingo.platform.event.IEventBus
import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteExisting
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.streams.toList

/**
 * Clears Xaero's cached map tiles and waypoints for finished bingo rounds, so the
 * explored map and path points from one game don't linger into the next and the
 * client doesn't accumulate a growing pile of per-round caches.
 *
 * Xaero stores data under `<gameDir>/xaero/{minimap,world-map}/<world>/<dimension>/`.
 * The two modules name dimension folders differently — the world-map uses the bare
 * id (`bingo$lobby`, `null`, …) while the minimap prefixes each with `dim%`
 * (`dim%bingo$lobby`, `dim%0`, …). Both schemes are recognised ([isBingoWorld],
 * [isLobbyDir]); missing the minimap's `dim%` prefix is what previously left
 * deathpoints (stored as ordinary minimap waypoints under the round's `dim%0`)
 * uncleaned, so they reappeared next game when Xaero reloaded them from disk.
 *
 * A bingo world (singleplayer save or LAN session) is identified by containing a
 * lobby dimension folder ([LOBBY_WORLD_ID]). Only such worlds are touched —
 * unrelated servers' maps are never deleted. Within a bingo world, every dimension
 * folder except the lobby belongs to an actual round (e.g. the game's `null`/
 * `dim%0` dimension, which holds both the explored tiles and any waypoints —
 * including deathpoints — the player placed during the round) and is removed.
 *
 * The lobby dimension itself is kept, but its per-round `mw$` multiworld folders are
 * pruned to just the newest one ([pruneStaleMultiworlds]): the server sends a fresh
 * Xaero world id on every return-to-lobby, which Xaero materializes as a new lobby
 * multiworld that it never cleans up — the main reason a persistent server's Xaero
 * cache grows without bound across games.
 *
 * Timing matters: Xaero holds a `.lock` on the active dimension while connected,
 * so cleanup runs on disconnect (when the bingo world's files are released) and on
 * client start (to mop up anything a previous session left locked). Deletion is
 * best-effort — files still held open are skipped and retried next time.
 *
 * It also runs when a game ends ([ClientGameEndEvent]) to drop the disk cache of
 * every round dimension *except the one the player is still in* (that one is locked
 * and is reset in memory by [XaeroMapResetter] instead). This is what wipes the
 * other dimensions a round visited (nether, end, …) so they reload empty next game,
 * rather than only being cleaned up on a later disconnect/restart.
 *
 * This only touches this client's local Xaero cache; it is the part a
 * server-side-only integration cannot do.
 */
internal class XaeroCacheCleaner(
    private val log: Logger,
    private val environment: IModEnvironment,
    private val executors: IExecutors,
    eventBus: IEventBus,
) : BingoComponent() {

    // The lobby dimension folder. Xaero's two modules name it differently:
    //   world-map: "bingo$lobby"
    //   minimap:   "dim%bingo$lobby"  (minimap prefixes every dimension with "dim%")
    // We must recognise both, or the minimap side is silently skipped (see [isLobbyDir]).
    private val lobbyDirName = LOBBY_WORLD_ID.toString().replace(':', '$')
    private val minimapLobbyDirName = "$MINIMAP_DIM_PREFIX$lobbyDirName"

    private companion object {
        // Xaero encodes the world display name into its folder name, turning the
        // "[BINGO]" prefix into "%lb%BINGO%rb%". Matching this marker identifies the
        // mod's ephemeral singleplayer worlds regardless of locale/world name suffix.
        const val BINGO_WORLD_FOLDER_MARKER = "%lb%BINGO%rb%"

        // Xaero names each multiworld folder "mw$<id>". The lobby accumulates one per
        // round (one per server-sent world id), so these are what we prune.
        const val MULTIWORLD_PREFIX = "mw\$"

        // Xaero's minimap prefixes every dimension folder with "dim%" (e.g. "dim%0",
        // "dim%bingo$lobby"); its world-map does not. Recognising this prefix is what
        // lets cleanup reach the minimap side, where deathpoints are stored.
        const val MINIMAP_DIM_PREFIX = "dim%"
    }

    private val cacheRoots: List<Path>
        get() = listOf(
            environment.gameDir.resolve("xaero").resolve("minimap"),
            environment.gameDir.resolve("xaero").resolve("world-map"),
        )

    init {
        // Mop up anything a previous session left behind (locked at the time).
        cleanLater()

        // Leaving the bingo world releases Xaero's file locks on the round's
        // dimension, so this is the reliable moment to delete it.
        eventBus.register(ClientServerEvent.Disconnect) { cleanLater() }

        // A game just ended: the player is still connected, so the dimension they
        // are in stays locked (reset in memory elsewhere), but every other round
        // dimension they visited is now unlocked and can be dropped right away so
        // the next round loads it empty instead of waiting for a disconnect.
        eventBus.register(ClientGameEndEvent) { cleanRoundDimensionsLater() }
    }

    private fun cleanLater() {
        executors.io.submit {
            try {
                cleanNow()
            } catch (e: Throwable) {
                log.warn("[XaeroCacheCleaner] Failed to clean Xaero round caches", e)
            }
        }
    }

    private fun cleanRoundDimensionsLater() {
        executors.io.submit {
            try {
                cleanRoundDimensionsNow()
            } catch (e: Throwable) {
                log.warn("[XaeroCacheCleaner] Failed to clean Xaero round dimensions", e)
            }
        }
    }

    /**
     * Delete the round dimensions of every bingo world, keeping each lobby. Unlike
     * [cleanNow] this never removes an ephemeral world wholesale (the player is still
     * in it), only its non-lobby dimensions — the active one is skipped by the
     * best-effort lock check.
     */
    private fun cleanRoundDimensionsNow() {
        for (root in cacheRoots) {
            if (!root.isDirectory()) continue
            for (worldDir in root.listChildren()) {
                if (!worldDir.isDirectory() || !isBingoWorld(worldDir)) continue
                clearRoundDimensionsOf(worldDir)
            }
        }
    }

    private fun cleanNow() {
        for (root in cacheRoots) {
            if (!root.isDirectory()) continue
            for (worldDir in root.listChildren()) {
                if (!worldDir.isDirectory()) continue

                when {
                    // Ephemeral singleplayer bingo worlds (the save is deleted on
                    // exit by BingoWorldManager) leave an orphaned Xaero folder per
                    // re-creation ("世界", "世界 (1)", ...). Their map is never reused,
                    // so remove the whole folder, lobby included, to stop the pile-up.
                    isEphemeralBingoWorld(worldDir) -> deleteRecursivelyBestEffort(worldDir)

                    // Persistent bingo worlds (e.g. a LAN/dedicated server that keeps
                    // its lobby): only clear the round dimensions, keep the lobby.
                    isBingoWorld(worldDir) -> clearRoundDimensionsOf(worldDir)

                    // Not a bingo world; never touch unrelated servers' maps.
                    else -> continue
                }
            }
        }
    }

    /**
     * Delete every round dimension of a bingo world (keeping the lobby), and within
     * the lobby prune all but its newest `mw$` multiworld.
     *
     * On a persistent server the lobby dimension is never removed, but it still grows
     * without bound: each game's return-to-lobby sends every client a fresh random
     * Xaero world id (see XaeroMapApi), which Xaero binds to a new server-based
     * `mw$<id>` multiworld folder under the lobby and never cleans up. Singleplayer
     * "looks" fine only because the whole save (and its Xaero cache) is deleted on
     * exit; a dedicated server keeps the lobby folder, so the `mw$` folders pile up
     * indefinitely (observed: 30+ on a long-running test server).
     *
     * The lobby is logically one fixed place that only ever needs one map, so we keep
     * just the most-recently-modified `mw$` (this round's) and drop the rest. Using
     * mtime avoids any race with which multiworld Xaero currently considers "active":
     * this runs on disconnect/startup when Xaero has released its locks, so it is a
     * pure on-disk prune with no dependency on live Xaero state.
     */
    private fun clearRoundDimensionsOf(worldDir: Path) {
        for (dimDir in worldDir.listChildren()) {
            if (!dimDir.isDirectory()) continue
            if (isLobbyDir(dimDir)) {
                pruneStaleMultiworlds(dimDir)
                continue
            }
            deleteRecursivelyBestEffort(dimDir)
        }
    }

    /**
     * Within a dimension folder, delete every `mw$` multiworld except the newest one
     * (by last-modified time), so the lobby's per-round multiworlds don't accumulate.
     * Non-`mw$` entries (e.g. `dimension_config.txt`, `caves`) are left untouched.
     */
    private fun pruneStaleMultiworlds(dimDir: Path) {
        val multiworlds = dimDir.listChildren()
            .filter { it.isDirectory() && it.name.startsWith(MULTIWORLD_PREFIX) }
        if (multiworlds.size <= 1) return

        val newest = multiworlds.maxByOrNull {
            runCatching { Files.getLastModifiedTime(it).toMillis() }.getOrDefault(Long.MIN_VALUE)
        }
        for (mw in multiworlds) {
            if (mw == newest) continue
            deleteRecursivelyBestEffort(mw)
        }
    }

    /**
     * The ExBingo singleplayer world name starts with [me.jfenn.bingo.common.BINGO_WORLD_PREFIX]
     * ("§a[BINGO]§f"). Xaero encodes the brackets, so its cache folder name contains
     * the "%lb%BINGO%rb%" marker. These saves are deleted on exit, so their cache is disposable.
     */
    private fun isEphemeralBingoWorld(worldDir: Path): Boolean =
        worldDir.name.contains(BINGO_WORLD_FOLDER_MARKER) && isBingoWorld(worldDir)

    private fun isBingoWorld(worldDir: Path): Boolean =
        worldDir.resolve(lobbyDirName).isDirectory() ||
                worldDir.resolve(minimapLobbyDirName).isDirectory()

    /** True for the lobby dimension folder in either Xaero module's naming scheme. */
    private fun isLobbyDir(dimDir: Path): Boolean =
        dimDir.name == lobbyDirName || dimDir.name == minimapLobbyDirName

    private fun Path.listChildren(): List<Path> =
        Files.list(this).use { it.toList() }

    private fun deleteRecursivelyBestEffort(path: Path) {
        var failed = false
        if (path.isDirectory()) {
            for (child in path.listChildren()) {
                if (!tryDeleteTree(child)) failed = true
            }
        }
        // Only remove the dimension folder itself once its contents are gone.
        if (!failed) {
            try {
                path.deleteExisting()
                log.info("[XaeroCacheCleaner] Cleared Xaero cache for {}", path.name)
            } catch (e: Throwable) {
                log.debug("[XaeroCacheCleaner] Could not remove {} (likely in use)", path, e)
            }
        }
    }

    /** Returns true if the whole tree was deleted. */
    private fun tryDeleteTree(path: Path): Boolean {
        var ok = true
        if (path.isDirectory()) {
            for (child in path.listChildren()) {
                if (!tryDeleteTree(child)) ok = false
            }
        }
        if (!ok) return false
        return try {
            path.deleteExisting()
            true
        } catch (e: Throwable) {
            log.debug("[XaeroCacheCleaner] Skipped locked/in-use file {}", path)
            false
        }
    }
}
