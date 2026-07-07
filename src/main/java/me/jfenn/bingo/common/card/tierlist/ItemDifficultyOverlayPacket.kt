package me.jfenn.bingo.common.card.tierlist

import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.platform.packet.PacketConverter
import net.minecraft.resources.ResourceLocation

data class ItemDifficultyOverlayPacket(
    val itemTiers: Map<String, TierLabel>,
    val advancementTiers: Map<String, TierLabel> = emptyMap(),
) {
    object V1 : PacketConverter<ItemDifficultyOverlayPacket> {
        override val id: ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID_BINGO, "item_difficulty_overlay")

        override fun toPacketBuf(source: ItemDifficultyOverlayPacket, dest: IPacketBuf) {
            dest.writeTierMap(source.itemTiers)
            dest.writeTierMap(source.advancementTiers)
        }

        override fun fromPacketBuf(buf: IPacketBuf): ItemDifficultyOverlayPacket {
            return ItemDifficultyOverlayPacket(
                itemTiers = buf.readTierMap(),
                advancementTiers = buf.readTierMap(),
            )
        }

        private fun IPacketBuf.writeTierMap(tiers: Map<String, TierLabel>) {
            val entries = tiers.entries.sortedBy { it.key }
            writeInt(entries.size)
            for ((id, tier) in entries) {
                writeString(id)
                writeString(tier.name)
            }
        }

        private fun IPacketBuf.readTierMap(): Map<String, TierLabel> {
            return buildMap {
                repeat(readInt()) {
                    put(readString(), TierLabel.valueOf(readString()))
                }
            }
        }
    }
}
