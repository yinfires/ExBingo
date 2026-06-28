package me.jfenn.bingo.client.integrations.xaero

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
 * A bingo world (singleplayer save or LAN session) is identified by containing a
 * `bingo$lobby` dimension folder ([LOBBY_WORLD_ID]). Only such worlds are touched —
 * unrelated servers' maps are never deleted. Within a bingo world, every dimension
 * folder except the lobby belongs to an actual round (e.g. the game's `null`/
 * `dim%0` dimension, which holds both the explored tiles and any waypoints the
 * player placed during the round) and is removed.
 *
 * Timing matters: Xaero holds a `.lock` on the active dimension while connected,
 * so cleanup runs on disconnect (when the bingo world's files are released) and on
 * client start (to mop up anything a previous session left locked). Deletion is
 * best-effort — files still held open are skipped and retried next time.
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

    private val lobbyDirName = LOBBY_WORLD_ID.toString().replace(':', '$')

    private companion object {
        // Xaero encodes the world display name into its folder name, turning the
        // "[BINGO]" prefix into "%lb%BINGO%rb%". Matching this marker identifies the
        // mod's ephemeral singleplayer worlds regardless of locale/world name suffix.
        const val BINGO_WORLD_FOLDER_MARKER = "%lb%BINGO%rb%"
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
                    isBingoWorld(worldDir) -> {
                        for (dimDir in worldDir.listChildren()) {
                            if (!dimDir.isDirectory()) continue
                            if (dimDir.name == lobbyDirName) continue
                            deleteRecursivelyBestEffort(dimDir)
                        }
                    }

                    // Not a bingo world; never touch unrelated servers' maps.
                    else -> continue
                }
            }
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
        worldDir.resolve(lobbyDirName).isDirectory()

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
