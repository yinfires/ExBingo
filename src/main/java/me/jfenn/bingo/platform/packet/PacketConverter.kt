package me.jfenn.bingo.platform.packet

import me.jfenn.bingo.platform.IPacketBuf
import net.minecraft.resources.ResourceLocation

interface PacketConverter<T> {

    val id: ResourceLocation

    fun toPacketBuf(source: T, dest: IPacketBuf)

    fun fromPacketBuf(buf: IPacketBuf): T

}