package me.jfenn.bingo.client.platform.screen

import me.jfenn.bingo.client.platform.renderer.IDrawService
import net.minecraft.client.gui.components.Renderable
import net.minecraft.client.gui.screens.Screen
import org.joml.Vector2i

interface IScreenHelper {
    val screen: Screen
    val width: Int
    val height: Int
    fun onAfterLeftClick(callback: (Vector2i) -> Unit)
    fun addButton(button: IButton)
    fun close()
}

interface IMutableScreenHelper : IScreenHelper {
    fun addDrawableChild(drawable: Renderable)
    fun addDrawable(drawable: IDrawable)
    fun clearChildren()
}

interface IDrawable {
    fun render(drawService: IDrawService)
}
