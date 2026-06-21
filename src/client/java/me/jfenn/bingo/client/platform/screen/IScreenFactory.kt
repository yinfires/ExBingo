package me.jfenn.bingo.client.platform.screen

import me.jfenn.bingo.platform.text.IText
import net.minecraft.client.gui.screens.Screen

interface IScreenFactory {
    fun build(title: IText, factory: (IMutableScreenHelper) -> IScreen): Screen
}