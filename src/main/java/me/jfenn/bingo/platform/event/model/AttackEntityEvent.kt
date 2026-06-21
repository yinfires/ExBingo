package me.jfenn.bingo.platform.event.model

import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.event.IReturnEvent
import net.minecraft.world.entity.Entity
import net.minecraft.server.MinecraftServer
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.level.Level

class AttackEntityEvent(
    val server: MinecraftServer,
    val player: IPlayerHandle,
    val world: Level,
    val hand: InteractionHand,
    val entity: Entity,
    val hit: EntityHitResult?,
) {
    companion object : IReturnEvent<AttackEntityEvent, ActionResult<Unit>?>
}