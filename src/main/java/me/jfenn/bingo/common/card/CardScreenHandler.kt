package me.jfenn.bingo.common.card

import me.jfenn.bingo.common.map.CardView
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.platform.item.IItemStackFactory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.inventory.ClickType

class CardScreenHandler(
    syncId: Int,
    view: CardView,
    itemStackFactory: IItemStackFactory,
    playerInventory: Inventory,
    text: TextProvider,
    ) : AbstractContainerMenu(MenuType.GENERIC_9x5, syncId) {

    init {
        val inventory = CardInventory(view, itemStackFactory, text)

        // bingo card slots
        for (row in 0 until 5) {
            for (col in 0 until 9) {
                val index = col + row * 9
                val x = 8 + col * 18
                val y = 18 + row * 18

                if (col in 2 until 7) {
                    this.addSlot(EmptySlot(inventory, index, x, y))
                } else {
                    this.addSlot(EmptySlot(inventory, index, x, y))
                }
            }
        }

        // player inventory
        val offset = 5*18 + 12
        for (row in 0 until 3) {
            for (col in 0 until 9) {
                this.addSlot(Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, offset + row * 18))
            }
        }

        // hotbar
        for (col in 0 until 9) {
            this.addSlot(Slot(playerInventory, col, 8 + col * 18, offset + 58))
        }
    }

    override fun moveItemStackTo(stack: ItemStack, startIndex: Int, endIndex: Int, reverseDirection: Boolean): Boolean {
        return false
    }

    override fun stillValid(player: Player): Boolean {
        return true
    }

    override fun slotsChanged(inventory: Container) {
    }

    override fun quickMoveStack(player: Player, slot: Int): ItemStack {
        return ItemStack.EMPTY
    }

    override fun clicked(slotIndex: Int, button: Int, actionType: ClickType, player: Player) {
    }
}


class EmptySlot(inventory: Container, index: Int, x: Int, y: Int) : Slot(inventory, index, x, y) {
    override fun mayPlace(stack: ItemStack): Boolean {
        return false
    }

    override fun mayPickup(playerEntity: Player): Boolean {
        return false
    }

    override fun isActive(): Boolean {
        return false
    }
}
