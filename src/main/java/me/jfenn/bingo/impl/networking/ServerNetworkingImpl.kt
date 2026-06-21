package me.jfenn.bingo.impl.networking

import me.jfenn.bingo.impl.PlayerHandle
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.item.IItemStackFactory
import me.jfenn.bingo.platform.packet.*
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import org.slf4j.Logger

class ServerNetworkingImpl(
    private val log: Logger,
    private val itemStackFactory: IItemStackFactory,
    private val eventBus: IEventBus,
) : IServerNetworking {

    override fun <T> registerC2S(converter: PacketConverter<T>): IServerPacketHandlerC2S<T>
        = ServerPacketHandlerC2S(converter)

    override fun <T> registerS2C(converter: PacketConverter<T>): IServerPacketHandlerS2C<T>
        = ServerPacketHandlerS2C(converter)

    inner class ServerPacketHandlerC2S<T>(
        private val converter: PacketConverter<T>,
    ) : IServerPacketHandlerC2S<T> {
        override val name: String = "packetC2S=${converter.id}"

        private val id: ResourceLocation = converter.id
        private val type: CustomPacketPayload.Type<BingoPayload<T>> =
            NeoForgePacketRegistry.registerC2S(converter, itemStackFactory, eventBus, this)

        init {
            log.debug("[ServerNetworkingInfo] Registering C2S packet {}", converter.id.toString())
        }
    }

    inner class ServerPacketHandlerS2C<T>(
        private val converter: PacketConverter<T>,
    ) : IServerPacketHandlerS2C<T> {

        private val id: ResourceLocation = converter.id
        private val type: CustomPacketPayload.Type<BingoPayload<T>> =
            NeoForgePacketRegistry.registerS2C(converter, itemStackFactory)

        init {
            log.debug("[ServerNetworkingInfo] Registering S2C packet {}", converter.id.toString())
        }

        override fun isSupported(player: ServerPlayer): Boolean {
            return NeoForgePacketRegistry.hasChannel(player, id)
        }

        override fun send(player: ServerPlayer, packet: T): Boolean {
            if (isSupported(player)) {
                PacketDistributor.sendToPlayer(player, BingoPayload(type, packet))
                return true
            } else {
                return false
            }
        }
    }
}
