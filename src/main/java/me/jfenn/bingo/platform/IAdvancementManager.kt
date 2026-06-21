package me.jfenn.bingo.platform

import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.item.IItemStack
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

interface IAdvancementManager {

    fun listAdvancements(server: MinecraftServer): List<String>

    fun getAdvancement(server: MinecraftServer, id: String): IAdvancementHandle?

    fun getProgress(player: ServerPlayer, advancement: IAdvancementHandle): Float

    fun isAnyObtained(player: ServerPlayer, advancement: IAdvancementHandle): Boolean

    fun isDone(player: ServerPlayer, advancement: IAdvancementHandle): Boolean

    fun clearAdvancements(player: ServerPlayer)

}

interface IAdvancementHandle {
    val name: IText?
    val description: IText?
    val displayIcon: IItemStack?
}
