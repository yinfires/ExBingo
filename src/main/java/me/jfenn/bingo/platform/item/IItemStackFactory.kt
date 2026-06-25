package me.jfenn.bingo.platform.item

import kotlinx.serialization.Contextual
import me.jfenn.bingo.platform.text.IText
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.server.MinecraftServer
import net.minecraft.resources.ResourceLocation

interface IItemStackFactory {
    val emptyStack: IItemStack

    fun listItems(server: MinecraftServer): List<String>

    /**
     * Lists the identifiers of all items whose placed block is unbreakable
     * (destroy time < 0, e.g. bedrock, barrier, command blocks, structure blocks).
     * These are typically not legitimately obtainable in survival.
     */
    fun listUnbreakableItems(server: MinecraftServer): List<String>

    fun isEnabledInWorld(item: String, server: MinecraftServer): Boolean

    fun createStack(item: String, count: Int = 1): IItemStack
    fun createStack(item: ResourceLocation, count: Int = 1): IItemStack
    fun createStack(item: Item, count: Int = 1): IItemStack
    fun forStack(stack: ItemStack?): IItemStack

    fun createFilledMap(): IFilledMap
    fun createFireworkRocket(): IFireworkRocket
    fun createWrittenBook(): IWrittenBook
    fun createPlayerHead(): IPlayerHead
}

interface IItemStack {
    val stack: ItemStack
    val item: Item
    val identifier: ResourceLocation
    var count: Int
    val maxCount: Int
    val isEmpty get() = count <= 0

    val displayName: IText
    val lore: List<IText>

    fun addCustomTag(tag: String)
    fun removeCustomTag(tag: String)
    fun hasCustomTag(tag: String): Boolean

    fun setDisplay(
        name: IText?,
        lore: List<IText>?,
    )

    fun setUnbreakable(value: Boolean)
    fun setHideFlags(hideFlags: Int)

    fun setNbtString(nbt: String?): Boolean
    fun getNbtString(): String?

    fun setComponentsString(components: Map<String, String?>): Boolean
    fun getComponentsString(): Map<String, String?>?

    fun isDataOverlapping(
        nbt: String?,
        components: Map<String, String?>?,
    ): Boolean

    fun copy(): IItemStack

    fun asFilledMap(): IFilledMap?
    fun asFireworkRocket(): IFireworkRocket?
    fun asWrittenBook(): IWrittenBook?
    fun asPlayerHead(): IPlayerHead?
}

typealias IItemStackSerialized = @Contextual IItemStack
