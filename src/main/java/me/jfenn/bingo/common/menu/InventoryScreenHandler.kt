package me.jfenn.bingo.common.menu

import me.jfenn.bingo.platform.item.IItemStack
import me.jfenn.bingo.common.NBT_BINGO_IGNORE
import me.jfenn.bingo.common.NBT_BINGO_KEEP
import me.jfenn.bingo.common.NBT_BINGO_VANISH
import me.jfenn.bingo.common.utils.EventListener
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.inventory.Slot
import org.koin.core.component.KoinComponent

internal class InventoryScreenHandler(
    syncId: Int,
    stacks: List<IItemStack>,
    playerInventory: Inventory,
    ) : AbstractContainerMenu(MenuType.GENERIC_9x3, syncId), KoinComponent {

    val inventory = SimpleContainer(9*3)

    val onClose = EventListener<List<ItemStack>>()

    init {
        stacks.forEachIndexed { i, stack ->
            // remove bingo flags when in a config inventory
            stack.removeCustomTag(NBT_BINGO_IGNORE)
            stack.removeCustomTag(NBT_BINGO_VANISH)
            stack.removeCustomTag(NBT_BINGO_KEEP)
            inventory.setItem(i, stack.stack)
        }

        // inventory slots
        for (row in 0 until 3) {
            for (col in 0 until 9) {
                val index = col + row * 9
                val x = 8 + col * 18
                val y = 18 + row * 18

                if (col in 2 until 7) {
                    this.addSlot(Slot(inventory, index, x, y))
                } else {
                    this.addSlot(Slot(inventory, index, x, y))
                }
            }
        }

        // player inventory
        val offset = 3*18 + 12
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

    override fun stillValid(player: Player): Boolean {
        return true
    }

    /**
     * Refer to GenericContainerScreenHandler for how this should be implemented
     */
    override fun quickMoveStack(player: Player, slot: Int): ItemStack {
        val slotInstance = slots[slot]
        if (!slotInstance.hasItem())
            return ItemStack.EMPTY

        val originalStack = slotInstance.item
        val newStack = originalStack.copy()

        if (slot < 3*9) {
            if (!moveItemStackTo(originalStack, 3*9, slots.size, true))
                return ItemStack.EMPTY
        } else {
            if (!moveItemStackTo(originalStack, 0, 3*9, false))
                return ItemStack.EMPTY
        }

        slotInstance.setChanged()
        return newStack
    }

    override fun removed(player: Player) {
        super.removed(player)
        onClose(
            (0 until inventory.containerSize)
                .map { inventory.getItem(it) }
                .filter { !it.isEmpty }
        )
    }
}
