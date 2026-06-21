package me.jfenn.bingo.impl

import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IRecipe
import me.jfenn.bingo.platform.IRecipeManager
import net.minecraft.world.item.crafting.RecipeHolder
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

class RecipeManagerImpl(
    private val server: MinecraftServer,
) : IRecipeManager {
    override fun listRecipes(): List<IRecipe> {
        return server.recipeManager.recipes
            .map { RecipeImpl(it) }
    }

    override fun lockRecipes(player: IPlayerHandle, recipes: List<IRecipe>) {
        val recipeEntries = recipes
            .filterIsInstance<RecipeImpl>()
            .map { it.recipe }

        val playerEntity: ServerPlayer = player.player
        playerEntity.resetRecipes(recipeEntries)
    }

    override fun unlockRecipes(player: IPlayerHandle, recipes: List<IRecipe>) {
        val recipeEntries = recipes
            .filterIsInstance<RecipeImpl>()
            .map { it.recipe }

        val playerEntity: ServerPlayer = player.player
        playerEntity.awardRecipes(recipeEntries)
    }
}

class RecipeImpl(val recipe: RecipeHolder<*>) : IRecipe
