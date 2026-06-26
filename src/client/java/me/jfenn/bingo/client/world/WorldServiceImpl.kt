package me.jfenn.bingo.client.world

import me.jfenn.bingo.client.platform.IWorldService
import me.jfenn.bingo.common.BINGO_WORLD_PREFIX
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import org.slf4j.Logger
import kotlin.io.path.name

class WorldServiceImpl(
    private val log: Logger,
    private val worldState: BingoWorldState,
) : IWorldService {

    private val client = Minecraft.getInstance()

    override fun createBingoWorld() {
        val parent = client.screen
        log.info("[WorldServiceImpl] createBingoWorld() invoked, opening CreateWorldScreen (parent={})", parent?.let { it::class.java.simpleName })
        worldState.state = ScreenState.CreateBingoWorld
        CreateWorldScreen.openFresh(client, parent)
    }

    override fun isBingoWorld(server: MinecraftServer): Boolean {
        return server.worldData.levelName.startsWith(BINGO_WORLD_PREFIX)
    }

    override fun deleteSave(server: MinecraftServer) {
        // If the game is not in progress, delete it!
        log.info("Deleting closed BINGO world save")

        val dirName = server.getWorldPath(LevelResource.ROOT).parent.name

        // The integrated server has just released its session.lock, but on some
        // platforms (notably Windows) the underlying file handle may not be fully
        // released yet, so re-acquiring the directory lock via createAccess() can
        // throw for a short window. Vanilla's deleteLevel() retries file deletion
        // but NOT the initial lock acquisition, so without this retry the world is
        // intermittently left behind. Retry a few times with a short backoff.
        var lastError: Exception? = null
        for (attempt in 1..MAX_DELETE_ATTEMPTS) {
            try {
                client.levelSource.createAccess(dirName).use {
                    it.deleteLevel()
                }
                log.info("Deleted BINGO world save '{}' on attempt {}", dirName, attempt)
                return
            } catch (e: Exception) {
                lastError = e
                log.warn(
                    "Failed to delete BINGO world save '{}' (attempt {}/{}): {}",
                    dirName, attempt, MAX_DELETE_ATTEMPTS, e.message,
                )
                if (attempt < MAX_DELETE_ATTEMPTS) {
                    try {
                        Thread.sleep(DELETE_RETRY_DELAY_MS)
                    } catch (interrupted: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }
        }

        log.error("Could not delete BINGO world save '$dirName' after $MAX_DELETE_ATTEMPTS attempts; it may remain in the world list", lastError)
    }

    private companion object {
        const val MAX_DELETE_ATTEMPTS = 10
        const val DELETE_RETRY_DELAY_MS = 200L
    }
}
