package me.jfenn.bingo.impl

import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.IAdvancementHandle
import me.jfenn.bingo.platform.IAdvancementManager
import me.jfenn.bingo.platform.item.IItemStack
import me.jfenn.bingo.platform.item.IItemStackFactory
import net.minecraft.advancements.AdvancementHolder
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.resources.ResourceLocation
import kotlin.jvm.optionals.getOrNull

class AdvancementManager(
    private val itemStackFactory: IItemStackFactory,
) : IAdvancementManager {
    override fun listAdvancements(server: MinecraftServer): List<String> {
        return server.advancements.allAdvancements
            .filter { !it.id().path.endsWith("/root") && it.value().display().isPresent }
            .map { it.id().toString() }
    }

    override fun getAdvancement(server: MinecraftServer, id: String): IAdvancementHandle? {
        return server.advancements.get(ResourceLocation.parse(id))
            ?.let { AdvancementHandle(it, itemStackFactory) }
    }

    override fun getProgress(player: ServerPlayer, advancement: IAdvancementHandle): Float {
        require(advancement is AdvancementHandle)
        return player.advancements.getOrStartProgress(advancement.advancement).percent
    }

    override fun isAnyObtained(player: ServerPlayer, advancement: IAdvancementHandle): Boolean {
        require(advancement is AdvancementHandle)
        return player.advancements.getOrStartProgress(advancement.advancement).hasProgress()
    }

    override fun isDone(player: ServerPlayer, advancement: IAdvancementHandle): Boolean {
        require(advancement is AdvancementHandle)
        return player.advancements.getOrStartProgress(advancement.advancement).isDone
    }

    override fun clearAdvancements(player: ServerPlayer) {
        for (advancement in player.server.advancements.allAdvancements) {
            val progress = player.advancements.getOrStartProgress(advancement)
            for (criteria in progress.completedCriteria) {
                player.advancements.revoke(advancement, criteria)
            }
        }
    }
}

class AdvancementHandle(
    val advancement: AdvancementHolder,
    private val itemStackFactory: IItemStackFactory,
) : IAdvancementHandle {
    override val name: IText?
        get() = advancement.value().display().getOrNull()?.title
            ?.let { TextImpl(it.copy()) }
    override val description: IText?
        get() = advancement.value().display().getOrNull()?.description
            ?.let { TextImpl(it.copy()) }
    override val displayIcon: IItemStack?
        get() = advancement.value().display().getOrNull()?.icon
            ?.let { itemStackFactory.forStack(it) }
}
