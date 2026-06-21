package me.jfenn.bingo.client.impl

import me.jfenn.bingo.client.platform.ClientPacket
import me.jfenn.bingo.client.platform.IClientNetworking
import me.jfenn.bingo.client.platform.IClientPacketHandlerC2S
import me.jfenn.bingo.client.platform.IClientPacketHandlerS2C
import me.jfenn.bingo.impl.networking.BingoPayload
import me.jfenn.bingo.impl.networking.NeoForgePacketRegistry
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.item.IItemStackFactory
import me.jfenn.bingo.platform.packet.PacketConverter
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.neoforged.neoforge.network.PacketDistributor
import org.slf4j.Logger

class ClientNetworkingImpl(
    private val log: Logger,
    private val itemStackFactory: IItemStackFactory,
    private val eventBus: IEventBus,
) : IClientNetworking {

    override fun <T> registerC2S(converter: PacketConverter<T>): IClientPacketHandlerC2S<T>
        = ClientPacketHandlerC2S(converter)

    override fun <T> registerS2C(converter: PacketConverter<T>): IClientPacketHandlerS2C<T>
        = ClientPacketHandlerS2C(converter)

    inner class ClientPacketHandlerC2S<T>(
        private val converter: PacketConverter<T>,
    ) : IClientPacketHandlerC2S<T> {

        private val type: CustomPacketPayload.Type<BingoPayload<T>> =
            NeoForgePacketRegistry.registerClientC2S(converter, itemStackFactory)

        init {
            log.debug("[ClientNetworkingImpl] Registering C2S packet {}", converter.id.toString())
        }

        override fun isSupported(): Boolean {
            return true
        }

        override fun send(packet: T): Boolean {
            if (isSupported()) {
                PacketDistributor.sendToServer(BingoPayload(type, packet))
                return true
            } else {
                return false
            }
        }
    }

    inner class ClientPacketHandlerS2C<T>(
        private val converter: PacketConverter<T>,
    ) : IClientPacketHandlerS2C<T> {
        override val name: String = "packetS2C=${converter.id}"

        private val type: CustomPacketPayload.Type<BingoPayload<T>> =
            NeoForgePacketRegistry.registerClientS2C(converter, itemStackFactory, eventBus, this)

        init {
            log.debug("[ClientNetworkingImpl] Registering S2C packet {}", converter.id.toString())
        }
    }
}
