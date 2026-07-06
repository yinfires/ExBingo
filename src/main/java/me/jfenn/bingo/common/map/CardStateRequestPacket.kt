package me.jfenn.bingo.common.map

import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.platform.packet.PacketConverter
import net.minecraft.resources.ResourceLocation

class CardStateRequestPacket {
    object V1 : PacketConverter<CardStateRequestPacket> {
        override val id: ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID_BINGO, "card_state_request")

        override fun toPacketBuf(source: CardStateRequestPacket, dest: IPacketBuf) {
            // no-op
        }

        override fun fromPacketBuf(buf: IPacketBuf): CardStateRequestPacket {
            return CardStateRequestPacket()
        }
    }
}
