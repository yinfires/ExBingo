package me.jfenn.bingo.platform.item

import net.minecraft.server.level.ServerPlayer

interface IPlayerHead : IItemStack {
    fun setSkullOwner(player: ServerPlayer)
}