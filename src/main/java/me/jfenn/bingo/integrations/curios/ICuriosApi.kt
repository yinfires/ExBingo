package me.jfenn.bingo.integrations.curios

import me.jfenn.bingo.platform.item.IItemStack
import net.minecraft.server.level.ServerPlayer

/**
 * Access to a player's Curios (accessory) inventory, when the Curios mod is installed.
 *
 * Implemented via the NeoForge entity capability that Curios registers under
 * `curios:item_handler`, so no compile- or run-time dependency on Curios is required:
 * when the mod is absent the capability is simply not present and every call is a no-op.
 */
interface ICuriosApi {
    /**
     * True if a Curios inventory is present for [player] (i.e. the mod is installed and the
     * player has at least one accessory slot type).
     */
    fun isInstalled(player: ServerPlayer): Boolean

    /**
     * Clears every accessory the player is wearing, except for stacks the [keep] predicate
     * returns true for. Used to fully reset player state between Bingo games — the vanilla
     * inventory clear in PlayerController does not cover modded accessory slots.
     */
    fun clearCurios(player: ServerPlayer, keep: (IItemStack) -> Boolean)
}
