package me.jfenn.bingo.client.common.teamchest

import me.jfenn.bingo.client.common.state.BingoHudState
import me.jfenn.bingo.client.platform.IClient
import me.jfenn.bingo.client.platform.IKeyBindingManager
import me.jfenn.bingo.client.platform.event.model.ClientTickEvent
import me.jfenn.bingo.common.KEYBIND_OPEN_TEAM_CHEST
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.event.IEventBus
import org.lwjgl.glfw.GLFW

internal class TeamChestKeybindController(
    keyBindingManager: IKeyBindingManager,
    eventBus: IEventBus,
    private val client: IClient,
    private val state: BingoHudState,
    private val text: TextProvider,
) {
    private val openTeamChestKey = keyBindingManager.registerKey(
        KEYBIND_OPEN_TEAM_CHEST,
        GLFW.GLFW_KEY_B,
        StringKey.FullName.key,
    )

    init {
        eventBus.register(ClientTickEvent.End) {
            if (client.isPaused)
                return@register

            if (state.gameStatus.isInGame && client.player.isSpectator) {
                client.player.sendHotbarMessage(
                    text.string(StringKey.HudSpectatorTeamSelect, openTeamChestKey.displayName)
                )
            }

            if (openTeamChestKey.wasPressed()) {
                client.player.sendCommand("teamchest")
            }
        }
    }
}
