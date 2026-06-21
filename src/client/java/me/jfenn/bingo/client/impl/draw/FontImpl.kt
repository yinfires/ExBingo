package me.jfenn.bingo.client.impl.draw

import me.jfenn.bingo.client.platform.renderer.IFont
import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.impl.TextImpl
import net.minecraft.client.gui.Font
import net.minecraft.util.FormattedCharSequence
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.Component
import java.util.*

class FontImpl(
    private val textRenderer: Font
): IFont {
    override fun getTextWidth(text: IText): Int {
        return textRenderer.width(text.value)
    }

    override fun getTextHeight(): Int {
        return textRenderer.lineHeight
    }

    override fun wrapLines(text: IText, width: Int): List<IText> {
        return textRenderer.split(text.value, width)
            .map { createText(it) }
    }

    override fun truncate(text: IText, width: Int): IText {
        return createText(textRenderer.substrByWidth(text.value, width))
    }

    private fun createText(visitable: FormattedText): IText {
        val line = Component.empty()
        val curStr = StringBuilder()
        var curStyle: Style = Style.EMPTY
        visitable.visit(
            { style, asString ->
                if (style != curStyle) {
                    line.append(Component.literal(curStr.toString()).setStyle(curStyle))
                    curStr.clear()
                    curStyle = style
                }
                curStr.append(asString)
                Optional.empty<Unit>()
            },
            Style.EMPTY
        )
        line.append(Component.literal(curStr.toString()).setStyle(curStyle))
        return TextImpl(line)
    }

    private fun createText(orderedText: FormattedCharSequence): IText {
        val line = Component.empty()
        val curStr = StringBuilder()
        var curStyle: Style = Style.EMPTY
        orderedText.accept { _, style, codePoint ->
            if (style != curStyle) {
                line.append(Component.literal(curStr.toString()).setStyle(curStyle))
                curStr.clear()
                curStyle = style
            }
            curStr.appendCodePoint(codePoint)
            true
        }
        line.append(Component.literal(curStr.toString()).setStyle(curStyle))
        return TextImpl(line)
    }
}
