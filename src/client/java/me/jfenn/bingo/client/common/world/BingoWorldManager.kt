package me.jfenn.bingo.client.common.world

import me.jfenn.bingo.client.platform.IWorldService
import me.jfenn.bingo.client.platform.event.model.ScreenEvent
import me.jfenn.bingo.client.platform.event.model.ScreenType
import me.jfenn.bingo.client.platform.screen.IButtonFactory
import me.jfenn.bingo.platform.text.ITextFactory
import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.game.ScopeStopped
import net.minecraft.server.MinecraftServer

internal class BingoWorldManager(
    eventBus: IEventBus,
    private val buttonFactory: IButtonFactory,
    private val worldService: IWorldService,
    private val config: BingoConfig,
    private val text: ITextFactory,
) : BingoComponent() {

    init {
        eventBus.register(ScopeStopped) {
            val state = it.scope.get<BingoState>()
            val server = it.scope.get<MinecraftServer>()

            // Always clean up a managed BINGO world on exit, even mid-game. Upstream
            // kept PLAYING worlds around so a game could be resumed, but for these
            // ephemeral singleplayer worlds that just leaves stale entries in the
            // world list (the bug reported here). isLobbyMode means this world was
            // created by us, so it is safe to delete.
            if (worldService.isBingoWorld(server) && state.isLobbyMode) {
                worldService.deleteSave(server)
            }
        }

        eventBus.register(ScreenEvent.AfterInit) {
            if (it.type == ScreenType.TitleScreen && config.client.showQuickStartButton) {
                it.screen.addButton(
                    buttonFactory.createButton(
                        x = 10,
                        y = it.screen.height - 70,
                        width = 80,
                        height = 50,
                        texture = "minecraft:bingo/button",
                        focusedTexture = "minecraft:bingo/button_focused",
                        onClick = { worldService.createBingoWorld() },
                        message = text.literal("BINGO!"),
                    )
                )
            }
        }
    }
}