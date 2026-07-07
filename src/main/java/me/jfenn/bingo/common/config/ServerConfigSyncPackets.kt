package me.jfenn.bingo.common.config

import kotlinx.serialization.encodeToString
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.utils.json
import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.platform.packet.PacketConverter
import net.minecraft.resources.ResourceLocation

object ServerConfigRequestPacket {
    object V1 : PacketConverter<ServerConfigRequestPacket> {
        override val id: ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID_BINGO, "server_config_request_v1")

        override fun toPacketBuf(source: ServerConfigRequestPacket, dest: IPacketBuf) = Unit

        override fun fromPacketBuf(buf: IPacketBuf): ServerConfigRequestPacket = ServerConfigRequestPacket
    }
}

data class ServerConfigUpdatePacket(
    val config: BingoConfig,
) {
    object V1 : PacketConverter<ServerConfigUpdatePacket> {
        override val id: ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID_BINGO, "server_config_update_v1")

        override fun toPacketBuf(source: ServerConfigUpdatePacket, dest: IPacketBuf) {
            dest.writeConfig(source.config)
        }

        override fun fromPacketBuf(buf: IPacketBuf): ServerConfigUpdatePacket =
            ServerConfigUpdatePacket(buf.readConfig())
    }
}

data class ServerConfigSnapshotPacket(
    val config: BingoConfig,
    val canEdit: Boolean,
) {
    object V1 : PacketConverter<ServerConfigSnapshotPacket> {
        override val id: ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID_BINGO, "server_config_snapshot_v1")

        override fun toPacketBuf(source: ServerConfigSnapshotPacket, dest: IPacketBuf) {
            dest.writeBoolean(source.canEdit)
            dest.writeConfig(source.config)
        }

        override fun fromPacketBuf(buf: IPacketBuf): ServerConfigSnapshotPacket =
            ServerConfigSnapshotPacket(
                canEdit = buf.readBoolean(),
                config = buf.readConfig(),
            )
    }
}

private fun IPacketBuf.writeConfig(config: BingoConfig) {
    writeByteArray(json.encodeToString(config).encodeToByteArray())
}

private fun IPacketBuf.readConfig(): BingoConfig =
    json.decodeFromString(readByteArray().decodeToString())
