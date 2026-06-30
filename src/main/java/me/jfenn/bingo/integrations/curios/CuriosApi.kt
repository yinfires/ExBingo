package me.jfenn.bingo.integrations.curios

import me.jfenn.bingo.platform.item.IItemStack
import me.jfenn.bingo.platform.item.IItemStackFactory
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.capabilities.EntityCapability
import net.neoforged.neoforge.items.IItemHandler
import net.neoforged.neoforge.items.IItemHandlerModifiable

/**
 * Curios integration backed by the NeoForge entity capability `curios:item_handler`.
 *
 * Curios registers an [IItemHandler] capability exposing every equipped accessory across all
 * of an entity's slot types. Referencing it through the capability (rather than Curios' own
 * API classes) means this compiles and runs without any dependency on the Curios mod: when
 * Curios is not installed the capability resolves to null and every method is a no-op.
 */
class CuriosApi(
    private val itemStackFactory: IItemStackFactory,
) : ICuriosApi {

    private companion object {
        // The capability Curios registers for accessing the accessory inventory.
        // See https://docs.illusivesoulworks.com/curios/inventory/basic-inventory
        val CURIOS_INVENTORY: EntityCapability<IItemHandler, Void?> =
            EntityCapability.createVoid(
                ResourceLocation.fromNamespaceAndPath("curios", "item_handler"),
                IItemHandler::class.java,
            )
    }

    override fun isInstalled(player: ServerPlayer): Boolean {
        return player.getCapability(CURIOS_INVENTORY, null) != null
    }

    override fun clearCurios(player: ServerPlayer, keep: (IItemStack) -> Boolean) {
        val handler: IItemHandler = player.getCapability(CURIOS_INVENTORY, null) ?: return

        for (i in 0 until handler.slots) {
            val stack = handler.getStackInSlot(i)
            if (stack.isEmpty) continue
            if (keep(itemStackFactory.forStack(stack))) continue

            when (handler) {
                is IItemHandlerModifiable -> handler.setStackInSlot(i, ItemStack.EMPTY)
                // Fallback for non-modifiable handlers: drain the stack in place.
                else -> stack.count = 0
            }
        }
    }
}
