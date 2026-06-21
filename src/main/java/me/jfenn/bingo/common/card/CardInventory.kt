package me.jfenn.bingo.common.card

import me.jfenn.bingo.common.map.CardTile
import me.jfenn.bingo.common.map.CardView
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.platform.item.IItemStack
import me.jfenn.bingo.platform.item.IItemStackFactory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack

class CardInventory(
    val view: CardView,
    val itemStackFactory: IItemStackFactory,
    val text: TextProvider,
) : Container {
    override fun clearContent() {
    }

    override fun getContainerSize(): Int {
        return 25
    }

    override fun isEmpty(): Boolean {
        return false
    }

    private fun copyDisplay(tile: CardTile, stack: IItemStack) {
        stack.setDisplay(
            tile.name
                ?.takeIf { it != stack.displayName }
                ?.let { text.empty().append(it).resetStyle() },
            tile.lore
                .takeIf { it.isNotEmpty() }
                ?.map { text.empty().append(it).resetStyle() }
        )
    }

    fun createTileItem(tile: CardTile): IItemStack {
        if (tile.isHidden) {
            return itemStackFactory.emptyStack
        }

        val tileItem = tile.image.item
        val stack = when {
            tile.isAchieved -> runCatching { itemStackFactory.createStack("minecraft:lime_stained_glass_pane") }
                .getOrNull()
                ?.also { copyDisplay(tile, it) }
                ?: tileItem
            tile.isLocked -> runCatching { itemStackFactory.createStack("minecraft:barrier") }
                .getOrNull()
                ?.also { copyDisplay(tile, it) }
                ?: tileItem
            tileItem != null -> tileItem
            else -> {
                kotlin.runCatching { itemStackFactory.createStack("minecraft:knowledge_book") }
                    .getOrNull()
                    ?.also { copyDisplay(tile, it) }
            }
        }

        return stack ?: itemStackFactory.emptyStack
    }

    val inventory = List(45) { slot ->
        val slotX = (slot % 9) - 2
        val slotY = slot / 9

        if (slotX !in 0 until 5 || slotY !in 0 until 5)
            return@List ItemStack.EMPTY

        val tile = view.tiles.getOrNull(slotX + slotY * 5)
            ?: return@List ItemStack.EMPTY

        createTileItem(tile).stack
    }

    override fun getItem(slot: Int): ItemStack {
        return inventory.getOrNull(slot) ?: ItemStack.EMPTY
    }

    override fun removeItem(slot: Int, amount: Int): ItemStack {
        return ItemStack.EMPTY
    }

    override fun removeItemNoUpdate(slot: Int): ItemStack {
        return ItemStack.EMPTY
    }

    override fun setItem(slot: Int, stack: ItemStack) {
    }

    override fun setChanged() {
    }

    override fun stillValid(player: Player): Boolean {
        return true
    }
}
