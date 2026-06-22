package me.jfenn.bingo.client.world

import com.google.common.collect.ImmutableList
import me.jfenn.bingo.client.impl.accessor
import me.jfenn.bingo.common.BINGO_WORLD_PREFIX
import me.jfenn.bingo.common.datapack.LobbyWorldService
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.world.level.DataPackConfig
import net.minecraft.world.level.WorldDataConfiguration
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.common.NeoForge
import org.slf4j.Logger

class BingoWorldController(
    private val log: Logger,
    private val worldState: BingoWorldState,
    private val lobbyWorldService: LobbyWorldService,
) {

    companion object {
        const val DATAPACK_FILE = "bingo.zip"
        const val DATAPACK_ID = "file/${DATAPACK_FILE}"
    }

    init {
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn::class.java) {
            worldState.state = null
            worldState.isApplyingLobbyDataPack = false
        }

        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut::class.java) {
            worldState.state = null
            worldState.isApplyingLobbyDataPack = false
        }

        // The bingo datapack is applied asynchronously by CreateWorldScreen.applyNewPackConfig,
        // which re-shows the CreateWorldScreen once the reload completes. We intercept that
        // setScreen via ScreenEvent.Opening - which fires before the screen is initialised or
        // rendered - so we can trigger world creation without the CreateWorldScreen flashing
        // on screen for a frame.
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Opening::class.java) { event ->
            if (worldState.isApplyingLobbyDataPack) return@addListener
            if (worldState.state != ScreenState.OpenBingoWorld) return@addListener

            val newScreen = event.newScreen
            if (newScreen is CreateWorldScreen) {
                log.info("[BingoWorldController] datapack reload finished, creating world from screen open")
                // Prevent the bare CreateWorldScreen from being shown (even for a frame) and
                // immediately kick off world creation from it instead.
                event.isCanceled = true
                createBingoWorld(newScreen)
            }
        }

        NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Pre::class.java) { event ->
            val screen = event.screen
            if (screen is CreateWorldScreen && worldState.state == ScreenState.CreateBingoWorld) {
                // Add a prefix to the names of bingo worlds
                val uiState = screen.uiState
                if (!uiState.name.startsWith(BINGO_WORLD_PREFIX)) {
                    uiState.name = "$BINGO_WORLD_PREFIX ${uiState.name}"
                }
            }
        }

        NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post::class.java) { event ->
            val screen = event.screen
            val state = worldState.state

            log.info(
                "[BingoWorldController] init screen [{}]: {} isApplyingLobbyDataPack={}",
                screen::class.java.simpleName,
                state,
                worldState.isApplyingLobbyDataPack
            )

            if (worldState.isApplyingLobbyDataPack) {
                // this flag prevents infinite recursion, as there is a setScreen call in applyDataPacks
                return@addListener
            }

            if (screen is CreateWorldScreen && (state == ScreenState.CreateBingoWorld || state == null)) {
                // Copy the built-in lobby data pack to the world creator's temp dir
                // - we want this to happen on every world creation, so that the pack is available
                //   even if the user has not specifically pressed the "BINGO!" button.
                screen.accessor.invokeGetDataPackTempDir()?.let {
                    lobbyWorldService.copyDataPack(it.resolve(DATAPACK_FILE))
                }

                val pair = screen.accessor.invokeGetDataPackSelectionSettings(screen.uiState.settings.dataConfiguration())
                    ?: return@addListener

                // this flag prevents infinite recursion, as there is a setScreen call in applyDataPacks
                worldState.isApplyingLobbyDataPack = true
                screen.accessor.invokeTryApplyNewDataPacks(pair.second, false) {}
                worldState.isApplyingLobbyDataPack = false

                if (state == ScreenState.CreateBingoWorld) {
                    if (DATAPACK_ID !in pair.second.availableIds) {
                        log.error("Bingo datapack installation has failed! This will probably cause a crash.")
                    }

                    // actually enable the bingo lobby datapack
                    pair.second.setSelected((pair.second.selectedIds + DATAPACK_ID).reversed())

                    val enabled = ImmutableList.copyOf(pair.second.selectedIds)
                    val disabled = pair.second.availableIds.filter { !enabled.contains(it) }
                    val dataConfiguration = WorldDataConfiguration(
                        DataPackConfig(enabled, disabled),
                        screen.uiState.settings.dataConfiguration().enabledFeatures()
                    )

                    worldState.state = ScreenState.OpenBingoWorld
                    log.info("[BingoWorldController] datapack enabled, applying new pack config (-> OpenBingoWorld)")
                    screen.accessor.invokeApplyNewPackConfig(pair.second, dataConfiguration) {}

                    return@addListener
                }
            }

            if (screen is CreateWorldScreen && state == ScreenState.OpenBingoWorld) {
                // Once the data pack has been applied, immediately create the world.
                log.info("[BingoWorldController] pack config applied, creating world")
                createBingoWorld(screen)

                return@addListener
            }

            if (screen is ConfirmScreen && (state == ScreenState.OpenBingoWorld || state == ScreenState.ConfirmExperimentalFeatures)) {
                // If there is a prompt to confirm the experimental datapack usage,
                // click yes automatically
                log.warn("Bypassing experimental warnings for a BINGO world")

                val confirmButton = event.listenersList
                    .filterIsInstance<Button>()
                    .find {
                        val content = it.message.contents as? TranslatableContents
                        content?.key == "gui.yes"
                    }

                worldState.state = null
                confirmButton?.onClick(0.0, 0.0)
                    ?: log.error("Error: could not find the experimental world features confirm button")
            }
        }
    }

    private fun createBingoWorld(screen: CreateWorldScreen) {
        worldState.state = ScreenState.ConfirmExperimentalFeatures
        log.info("[BingoWorldController] Creating BINGO world after applying lobby datapack")
        screen.accessor.invokeOnCreate()
    }

}
