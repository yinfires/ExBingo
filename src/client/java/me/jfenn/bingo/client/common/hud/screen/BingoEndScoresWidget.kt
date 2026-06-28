package me.jfenn.bingo.client.common.hud.screen

import me.jfenn.bingo.client.common.state.BingoHudState
import me.jfenn.bingo.client.platform.IClient
import me.jfenn.bingo.client.platform.IScrollableWidget
import me.jfenn.bingo.client.platform.renderer.IDrawService
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.common.utils.formatHHMMSS
import me.jfenn.bingo.common.utils.minus
import me.jfenn.bingo.generated.StringKey
import net.minecraft.ChatFormatting
import org.koin.core.Koin
import java.time.Instant

internal class BingoEndScoresWidget(
    koin: Koin,
    private val gameOver: BingoHudState.GameOver,
    private val client: IClient = koin.get(),
    private val text: TextProvider = koin.get(),
) : IScrollableWidget() {

    private val entryHeight = client.font.getTextHeight() + 8

    override fun measureContentHeight(): Int {
        return entryHeight * (gameOver.packet.scores.size + 1)
    }

    override fun renderContents(drawService: IDrawService, mouseX: Int, mouseY: Int) {
        val teamCol = 4
        val scoreCol = (contentWidth * 0.4f).toInt()
        val timeCol = (contentWidth * 0.8f).toInt()

        val yOffset = (entryHeight/2) - (client.font.getTextHeight()/2)

        drawService.drawText(
            text.string(StringKey.GameEndScoresTeam).formatted(ChatFormatting.GRAY),
            teamCol, yOffset,
            0xFF_FFFFFF.toInt(), true,
        )

        drawService.drawText(
            text.string(StringKey.GameEndScoresScore).formatted(ChatFormatting.GRAY),
            scoreCol, yOffset,
            0xFF_FFFFFF.toInt(), true,
        )

        drawService.drawText(
            text.string(StringKey.GameEndScoresTime).formatted(ChatFormatting.GRAY),
            timeCol, yOffset,
            0xFF_FFFFFF.toInt(), true,
        )

        val now = Instant.now()
        for ((i, score) in gameOver.packet.scores.withIndex()) {
            val isObfuscated = (now - gameOver.packet.endedAt).seconds < (i+1).toLong()

            val teamText = text.empty()
                .append("#${score.index+1} ")
                .append(score.name)
                .let { if (isObfuscated) it.formatted(ChatFormatting.OBFUSCATED) else it }

            val scoreText = text.empty()
                .append(score.score.formatText(text))
                .let { if (isObfuscated) it.formatted(ChatFormatting.OBFUSCATED) else it }

            val durationText = text.literal(score.duration?.formatHHMMSS() ?: "DNF")
                .let { if (isObfuscated) it.formatted(ChatFormatting.OBFUSCATED) else it }

            val y = (i + 1) * entryHeight + yOffset
            // Clip the team text so long/multi-player team names don't overflow
            // into the score column (which showed as half-rendered names).
            val teamColMaxWidth = (scoreCol - teamCol - 4).coerceAtLeast(0)
            val teamTextClipped = client.font.truncate(teamText, teamColMaxWidth)
            drawService.drawText(
                teamTextClipped,
                teamCol, y,
                0xFF_FFFFFF.toInt(), true
            )

            drawService.drawText(
                scoreText,
                scoreCol, y,
                0xFF_FFFFFF.toInt(), true
            )

            drawService.drawText(
                durationText,
                timeCol, y,
                0xFF_FFFFFF.toInt(), true
            )

            val isTextHovered = mouseX in teamCol..(teamCol + client.font.getTextWidth(teamTextClipped))
                    && mouseY in (y - yOffset)..(y - yOffset + entryHeight)
            if (isTextHovered) {
                val view = gameOver.cards.find { it.teamKey == score.key }
                if (view != null) {
                    drawService.drawTooltip(view.display.players.map { text.literal(it.name) }, mouseX, mouseY)
                }
            }
        }
    }
}