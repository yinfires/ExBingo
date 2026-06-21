package me.jfenn.bingo.client.integrations.jei

import net.minecraft.world.item.ItemStack

interface IJeiApi {
    fun openItemRecipe(stack: ItemStack): Boolean
}