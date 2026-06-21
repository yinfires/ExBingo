package me.jfenn.bingo.client.impl

import me.jfenn.bingo.client.impl.draw.DrawService
import me.jfenn.bingo.client.platform.IScrollableWidget
import me.jfenn.bingo.client.platform.IScrollableWidgetFactory
import net.minecraft.client.gui.components.AbstractScrollWidget
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component

class ScrollableWidget(
    private val content: IScrollableWidget,
) : AbstractScrollWidget(0, 0, 0, 0, Component.empty()) {
    // override the hardcoded padding values
    private var contentPadding = 4

    override fun innerPadding(): Int {
        return contentPadding
    }

    // don't draw the box background
    override fun renderBackground(context: GuiGraphics) {}
    override fun renderBorder(context: GuiGraphics, x: Int, y: Int, width: Int, height: Int) {}

    override fun updateWidgetNarration(builder: NarrationElementOutput) {}

    override fun getInnerHeight(): Int {
        return content.measureContentHeight()
    }

    override fun scrollRate(): Double {
        return 9.0
    }

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        setRectangle(content.x, content.y, content.width, content.height)
        this.contentPadding = content.padding

        super.renderWidget(context, mouseX, mouseY, delta)
    }

    override fun renderContents(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val drawService = DrawService(context)
        drawService.delta = delta

        if (content.background != 0) {
            context.fill(x, y, x + width, y + innerHeight + contentPadding*2, 0, content.background)
        }

        context.pose().pushPose()
        context.pose().translate((x + contentPadding).toFloat(), (y + contentPadding).toFloat(), 0.0f)
        content.renderContents(drawService, mouseX - (x + contentPadding), mouseY - (y + contentPadding))
        context.pose().popPose()
    }
}

object ScrollableWidgetFactory : IScrollableWidgetFactory {
    override fun create(widget: IScrollableWidget): IScrollableWidget {
        widget.widget = ScrollableWidget(widget)
        return widget
    }
}
