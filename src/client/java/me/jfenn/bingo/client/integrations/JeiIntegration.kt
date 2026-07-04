package me.jfenn.bingo.client.integrations

import me.jfenn.bingo.client.integrations.jei.IJeiApi
import net.minecraft.world.item.ItemStack

internal class JeiIntegration(
    private val integrations: List<IJeiApi>,
) : IJeiApi {

    override fun openItemRecipe(stack: ItemStack): Boolean {
        return integrations.find { it.openItemRecipe(stack) } != null
    }

    override fun openItemUses(stack: ItemStack): Boolean {
        return integrations.find { it.openItemUses(stack) } != null
    }

    override fun handleHoveredStackKey(keyCode: Int, scanCode: Int, stack: ItemStack): Boolean {
        return integrations.find { it.handleHoveredStackKey(keyCode, scanCode, stack) } != null
    }
}
