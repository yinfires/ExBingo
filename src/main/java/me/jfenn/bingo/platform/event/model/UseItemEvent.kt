package me.jfenn.bingo.platform.event.model

import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.event.IReturnEvent
import me.jfenn.bingo.platform.item.IItemStack
import net.minecraft.server.MinecraftServer
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.Level

class UseItemEvent(
    val server: MinecraftServer,
    val player: IPlayerHandle,
    val world: Level,
    val hand: InteractionHand,
) {
    companion object : IReturnEvent<UseItemEvent, ActionResult<IItemStack?>?>
}
