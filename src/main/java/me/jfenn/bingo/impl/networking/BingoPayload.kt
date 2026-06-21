package me.jfenn.bingo.impl.networking

import net.minecraft.network.protocol.common.custom.CustomPacketPayload

class BingoPayload<T>(
    private val payloadType: CustomPacketPayload.Type<BingoPayload<T>>,
    val value: T,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = payloadType
}
