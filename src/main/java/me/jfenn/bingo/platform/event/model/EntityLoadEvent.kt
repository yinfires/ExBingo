package me.jfenn.bingo.platform.event.model

import me.jfenn.bingo.platform.event.IEvent
import net.minecraft.world.entity.Entity
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel

data class EntityLoadEvent(
    val server: MinecraftServer,
    val entity: Entity,
    val world: ServerLevel
) {
    companion object : IEvent<EntityLoadEvent>
}