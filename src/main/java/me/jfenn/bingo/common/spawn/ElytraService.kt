package me.jfenn.bingo.common.spawn

import me.jfenn.bingo.common.NBT_BINGO_IGNORE
import me.jfenn.bingo.common.NBT_BINGO_KEEP
import me.jfenn.bingo.common.NBT_BINGO_VANISH
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.item.IItemStack
import me.jfenn.bingo.platform.item.IItemStackFactory
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items

class ElytraService(
    private val itemStackFactory: IItemStackFactory,
) {

    /**
     * Equip the player with elytra and firework rockets.
     * Only runs if config.isElytra is true
     */
    fun giveElytra(player: IPlayerHandle) {
        val bingoEquipment = player.allHeldStacks()
            .filter { it.hasCustomTag(NBT_BINGO_VANISH) }
            .groupBy { it.item }

        val hasElytra = bingoEquipment.has(Items.ELYTRA)

        if (!hasElytra) {
            // insert elytra in armor slot
            player.giveOrEquipStack(
                itemStackFactory.createStack(Items.ELYTRA).apply {
                    // unbreakable (should not take damage)
                    setUnbreakable(true)
                    addCustomTag(NBT_BINGO_IGNORE)
                    addCustomTag(NBT_BINGO_VANISH)
                    addCustomTag(NBT_BINGO_KEEP)
                }
            )
        }

        // also give rocket
        val rocketStack = bingoEquipment[Items.FIREWORK_ROCKET]?.firstOrNull()

        if (rocketStack != null && rocketStack.count < 2) {
            rocketStack.count = 2
        }

        if (rocketStack == null) {
            player.giveItemStack(
                itemStackFactory.createFireworkRocket().apply {
                    addCustomTag(NBT_BINGO_IGNORE)
                    addCustomTag(NBT_BINGO_VANISH)
                    addCustomTag(NBT_BINGO_KEEP)
                    fireworks = emptyList()
                }
            )
        }
    }

    private fun Map<Item, List<IItemStack>>.has(item: Item): Boolean {
        return this[item]?.isNotEmpty() == true
    }

    fun takeElytra(player: IPlayerHandle) {
        player.allHeldStacks()
            .filter { it.item == Items.ELYTRA || it.item == Items.FIREWORK_ROCKET }
            .filter { it.hasCustomTag(NBT_BINGO_VANISH) }
            .forEach {
                it.count = 0
            }
    }

}
