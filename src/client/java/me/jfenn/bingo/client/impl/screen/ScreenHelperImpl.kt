package me.jfenn.bingo.client.impl.screen

import me.jfenn.bingo.client.impl.draw.DrawService
import me.jfenn.bingo.client.impl.screen.button.ButtonImpl
import me.jfenn.bingo.client.platform.screen.IButton
import me.jfenn.bingo.client.platform.screen.IDrawable
import me.jfenn.bingo.client.platform.screen.IMutableScreenHelper
import me.jfenn.bingo.client.platform.screen.IScreenHelper
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Renderable
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.common.NeoForge
import org.joml.Vector2i
import org.lwjgl.glfw.GLFW

open class ScreenHelperImpl(
    override val screen: Screen,
    private val addListener: ((GuiEventListener) -> Unit)? = null,
) : IScreenHelper {
    override val width get() = screen.width
    override val height get() = screen.height

    override fun onAfterLeftClick(callback: (Vector2i) -> Unit) {
        NeoForge.EVENT_BUS.addListener(ScreenEvent.MouseButtonPressed.Post::class.java) { event ->
            if (event.screen == screen && event.button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
                callback(Vector2i(event.mouseX.toInt(), event.mouseY.toInt()))
        }
    }

    override fun addButton(button: IButton) {
        require(button is ButtonImpl)
        val mutableScreen = screen as? ScreenImpl
        if (mutableScreen != null) {
            mutableScreen.bingoAddDrawableChild(button.button)
        } else {
            addListener?.invoke(button.button)
        }
    }

    override fun close() {
        screen.onClose()
    }
}

class MutableScreenHelperImpl(
    private val screenImpl: ScreenImpl,
) : ScreenHelperImpl(screenImpl), IMutableScreenHelper {
    override fun addDrawable(drawable: IDrawable) {
        screenImpl.bingoAddDrawableChild(object : Renderable, GuiEventListener, NarratableEntry {
            override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, deltaTicks: Float) {
                drawable.render(DrawService(context))
            }

            override fun setFocused(focused: Boolean) {}
            override fun isFocused(): Boolean = false
            override fun narrationPriority(): NarratableEntry.NarrationPriority = NarratableEntry.NarrationPriority.NONE
            override fun updateNarration(builder: NarrationElementOutput) {}
        })
    }

    override fun addDrawableChild(drawable: Renderable) {
        screenImpl.bingoAddDrawableChild(drawable)
    }

    override fun clearChildren() {
        screenImpl.bingoClearChildren()
    }
}
