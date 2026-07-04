package me.jfenn.bingo.client.impl.screen

import me.jfenn.bingo.client.impl.draw.DrawService
import me.jfenn.bingo.client.platform.screen.IScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Renderable
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import kotlin.math.absoluteValue

class ScreenImpl(title: Component) : Screen(title) {
    var impl: IScreen? = null
    val helper = MutableScreenHelperImpl(this)

    override fun init() {
        super.init()
        impl?.init()
    }

    fun bingoAddDrawableChild(drawable: Renderable) {
        require(drawable is GuiEventListener) { "${drawable::class.simpleName} is not GuiEventListener!" }
        require(drawable is NarratableEntry) { "${drawable::class.simpleName} is not GuiEventListener!" }
        addRenderableWidget(drawable)
    }

    fun bingoClearChildren() {
        clearWidgets()
    }

    override fun resize(client: Minecraft?, width: Int, height: Int) {
        super.resize(client, width, height)
        impl?.resize(width, height)
    }

    override fun render(context: GuiGraphics?, mouseX: Int, mouseY: Int, delta: Float) {
        if (context == null) return
        val service = DrawService(context)
        impl?.beforeRender(service)
        super.render(context, mouseX, mouseY, delta)
        impl?.render(service, mouseX, mouseY, delta)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        return impl?.mouseDragged(mouseX, mouseY) == true || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        return impl?.mouseReleased(mouseX, mouseY) == true || super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        return impl?.mouseClicked(mouseX, mouseY, button) == true || super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double
    ): Boolean {
        val amount = arrayOf(horizontalAmount, verticalAmount).maxBy { it.absoluteValue }
        return impl?.mouseScrolled(mouseX, mouseY, amount) == true || super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        return impl?.keyPressed(KeyInputImpl(keyCode, scanCode)) == true || super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun isPauseScreen(): Boolean {
        return impl?.shouldPause() ?: false
    }

    override fun shouldCloseOnEsc(): Boolean {
        return impl?.shouldCloseOnEsc() ?: true
    }

    override fun onClose() {
        impl?.onClose()
        super.onClose()
    }
}
