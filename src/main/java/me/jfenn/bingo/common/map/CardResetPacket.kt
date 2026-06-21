package me.jfenn.bingo.common.map

import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.platform.packet.PacketConverter
import net.minecraft.resources.ResourceLocation

class CardResetPacket {
    object V1 : PacketConverter<CardResetPacket> {
        override val id: ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID_BINGO, "card_reset")

        override fun toPacketBuf(source: CardResetPacket, dest: IPacketBuf) {
            // no-op
        }

        override fun fromPacketBuf(buf: IPacketBuf): CardResetPacket {
            return CardResetPacket()
        }
    }
}
