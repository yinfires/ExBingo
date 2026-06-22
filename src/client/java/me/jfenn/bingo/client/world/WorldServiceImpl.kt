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
        client.levelSource.createAccess(dirName).use {
            it.deleteLevel()
        }
    }

}
