package me.jfenn.bingo.client.impl

import me.jfenn.bingo.client.platform.IOptionsAccessor
import net.minecraft.client.Minecraft

object OptionsAccessor : IOptionsAccessor {
    private val client = Minecraft.getInstance()

    override fun isDebugEnabled(): Boolean {
        return client.gui.debugOverlay.showDebugScreen()
    }

    override fun isPlayerListPressed(): Boolean {
        return client.options.keyPlayerList.isDown
    }

    override fun isSneakPressed(): Boolean {
        return client.options.keyShift.isDown
    }

    override fun isHudHidden(): Boolean {
        return client.options.hideGui
    }
}
