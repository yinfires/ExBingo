package me.jfenn.bingo.platform.event.model

import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.event.IReturnEvent
import net.minecraft.server.MinecraftServer
import net.minecraft.world.InteractionHand
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level

class AttackBlockEvent(
    val server: MinecraftServer,
    val player: IPlayerHandle,
    val world: Level,
    val hand: InteractionHand,
    val blockPos: BlockPos,
    val direction: Direction,
) {
    companion object : IReturnEvent<AttackBlockEvent, ActionResult<Unit>>
}
