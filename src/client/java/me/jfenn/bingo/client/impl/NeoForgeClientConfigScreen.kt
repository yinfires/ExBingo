package me.jfenn.bingo.client.impl

import net.neoforged.fml.ModContainer
import net.neoforged.neoforge.client.gui.ConfigurationScreen
import net.neoforged.neoforge.client.gui.IConfigScreenFactory

object NeoForgeClientConfigScreen {
    @JvmStatic
    fun register(modContainer: ModContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory::class.java) {
            IConfigScreenFactory { container, parent -> ConfigurationScreen(container, parent) }
        }
    }
}
