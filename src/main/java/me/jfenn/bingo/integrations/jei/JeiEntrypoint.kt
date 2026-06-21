package me.jfenn.bingo.integrations.jei

import mezz.jei.api.IModPlugin
import mezz.jei.api.JeiPlugin
import mezz.jei.api.runtime.IJeiRuntime
import net.minecraft.resources.ResourceLocation

@JeiPlugin
class JeiEntrypoint : IModPlugin {

    companion object {
        private val IDENTIFIER = ResourceLocation.fromNamespaceAndPath("exbingo", "bingo")

        var runtime: IJeiRuntime? = null
    }

    override fun getPluginUid(): ResourceLocation = IDENTIFIER

    override fun onRuntimeAvailable(jeiRuntime: IJeiRuntime) {
        JeiEntrypoint.runtime = jeiRuntime
    }

    override fun onRuntimeUnavailable() {
        JeiEntrypoint.runtime = null
    }
}
