package me.jfenn.bingo.common.menu

import me.jfenn.bingo.common.options.BingoOptions
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.stats.StatsService
import me.jfenn.bingo.common.utils.formatHHMMSS
import me.jfenn.bingo.common.utils.plus
import me.jfenn.bingo.generated.StringKey
import net.minecraft.server.MinecraftServer
import net.minecraft.network.chat.Component
import net.minecraft.ChatFormatting
import org.joml.Vector3d
import java.time.Duration
import java.util.concurrent.CompletableFuture

private var statsHash: String = ""
private var statsFuture: CompletableFuture<Duration?> = CompletableFuture.completedFuture(null)

internal const val MENU_PLAYER_COUNT_HEIGHT = 2*MENU_LINE_HEIGHT + 4*MENU_LINE_PADDING

internal fun MenuComponent.registerPlayerCount(
    position: Vector3d,
    width: Double,
    server: MinecraftServer = koinScope.get(),
    state: BingoState = koinScope.get(),
    options: BingoOptions = koinScope.get(),
    stats: StatsService = koinScope.get(),
) {
    onTick {
        val playerCount = server.playerCount
        val optionsHash = options.getShaHash()
        val hash = "${state.gameId}:$playerCount:$optionsHash"

        // if the options change, create a new future
        if (statsHash != hash) {
            statsHash = hash
            statsFuture = stats.getBestTimeAsync(
                options = options,
                isSingleplayer = server.isSingleplayer && server.playerCount <= 1
            ).whenComplete { _, _ ->
                markDirty()
            }
        }
    }

    val offsetY = 2*MENU_LINE_PADDING

    registerTitlePanel(
        position = position + Vector3d(0.0, -offsetY - MENU_LINE_HEIGHT, 0.0),
        width = width,
        titleProp = computedProperty {
            val bestTime = statsFuture.getNow(null)
                ?.formatHHMMSS()
                ?.let { text.from(Component.literal(it).withStyle(ChatFormatting.GREEN)) }
                ?: text.string(StringKey.StatsBestTimeNotSet).formatted(ChatFormatting.GRAY)

            text.string(
                StringKey.StatsBestTime,
                bestTime
            )
        },
    )

    registerTitlePanel(
        position = position + Vector3d(0.0, -offsetY - MENU_LINE_PADDING - 2*MENU_LINE_HEIGHT, 0.0),
        width = width,
        titleProp = computedProperty {
            text.string(
                StringKey.OptionsPlayerCount,
                Component.literal("${server.playerCount} / ${server.maxPlayers}")
                    .withStyle(ChatFormatting.GREEN)
            )
        },
    )
}
