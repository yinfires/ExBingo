package me.jfenn.bingo.impl.networking

import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.impl.PlayerHandle
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.item.IItemStackFactory
import me.jfenn.bingo.platform.packet.IServerPacketHandlerC2S
import me.jfenn.bingo.platform.packet.PacketConverter
import me.jfenn.bingo.platform.packet.ServerPacket
import net.minecraft.network.ConnectionProtocol
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext
import net.neoforged.neoforge.network.registration.NetworkRegistry
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

object NeoForgePacketRegistry {
    private val log = LoggerFactory.getLogger(NeoForgePacketRegistry::class.java)
    private val c2s = ConcurrentHashMap<ResourceLocation, PacketRegistration<*>>()
    private val s2c = ConcurrentHashMap<ResourceLocation, PacketRegistration<*>>()

    fun <T> registerC2S(
        converter: PacketConverter<T>,
        itemStackFactory: IItemStackFactory,
        eventBus: IEventBus,
        eventType: IServerPacketHandlerC2S<T>,
    ): CustomPacketPayload.Type<BingoPayload<T>> {
        val type = typeOf<T>(converter.id)
        c2s.compute(converter.id) { _, existing ->
            @Suppress("UNCHECKED_CAST")
            (existing as? PacketRegistration<T>)
                ?.withServerHandler { payload, context ->
                    val player = PlayerHandle(context.player() as ServerPlayer)
                    val data = ServerPacket(player, payload.value)
                    context.enqueueWork {
                        eventBus.emit(eventType, data)
                    }
                }
                ?: PacketRegistration(
                    type = type,
                    converter = converter,
                    itemStackFactory = itemStackFactory,
                    serverHandler = { payload, context ->
                        val player = PlayerHandle(context.player() as ServerPlayer)
                        val data = ServerPacket(player, payload.value)
                        context.enqueueWork {
                            eventBus.emit(eventType, data)
                        }
                    },
                    clientHandler = null,
                )
        }
        return type
    }

    fun <T> registerS2C(converter: PacketConverter<T>, itemStackFactory: IItemStackFactory): CustomPacketPayload.Type<BingoPayload<T>> {
        val type = typeOf<T>(converter.id)
        s2c.computeIfAbsent(converter.id) {
            PacketRegistration(
                type = type,
                converter = converter,
                itemStackFactory = itemStackFactory,
                serverHandler = null,
                clientHandler = null,
            )
        }
        return type
    }

    fun <T> registerClientC2S(converter: PacketConverter<T>, itemStackFactory: IItemStackFactory): CustomPacketPayload.Type<BingoPayload<T>> {
        val type = typeOf<T>(converter.id)
        c2s.computeIfAbsent(converter.id) {
            PacketRegistration(
                type = type,
                converter = converter,
                itemStackFactory = itemStackFactory,
                serverHandler = null,
                clientHandler = null,
            )
        }
        return type
    }

    fun <T> registerClientS2C(
        converter: PacketConverter<T>,
        itemStackFactory: IItemStackFactory,
        eventBus: IEventBus,
        eventType: me.jfenn.bingo.client.platform.IClientPacketHandlerS2C<T>,
    ): CustomPacketPayload.Type<BingoPayload<T>> {
        val type = typeOf<T>(converter.id)
        s2c.compute(converter.id) { _, existing ->
            @Suppress("UNCHECKED_CAST")
            (existing as? PacketRegistration<T>)
                ?.withClientHandler { payload, context ->
                    val data = me.jfenn.bingo.client.platform.ClientPacket(payload.value)
                    context.enqueueWork {
                        eventBus.emit(eventType, data)
                    }
                }
                ?: PacketRegistration(
                    type = type,
                    converter = converter,
                    itemStackFactory = itemStackFactory,
                    serverHandler = null,
                    clientHandler = { payload, context ->
                        val data = me.jfenn.bingo.client.platform.ClientPacket(payload.value)
                        context.enqueueWork {
                            eventBus.emit(eventType, data)
                        }
                    },
                )
        }
        return type
    }

    fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar(MOD_ID_BINGO).optional()
        c2s.values.forEach {
            @Suppress("UNCHECKED_CAST")
            (it as PacketRegistration<Any>).registerC2S(registrar)
        }
        s2c.values.forEach {
            @Suppress("UNCHECKED_CAST")
            (it as PacketRegistration<Any>).registerS2C(registrar)
        }
    }

    fun hasChannel(player: ServerPlayer, id: ResourceLocation): Boolean {
        return runCatching {
            NetworkRegistry.hasChannel(player.connection, id)
        }.getOrDefault(true)
    }

    private fun <T> typeOf(id: ResourceLocation): CustomPacketPayload.Type<BingoPayload<T>> {
        return CustomPacketPayload.Type(id)
    }

    private data class PacketRegistration<T>(
        val type: CustomPacketPayload.Type<BingoPayload<T>>,
        val converter: PacketConverter<T>,
        val itemStackFactory: IItemStackFactory,
        val serverHandler: ((BingoPayload<T>, IPayloadContext) -> Unit)?,
        val clientHandler: ((BingoPayload<T>, IPayloadContext) -> Unit)?,
    ) {
        private val codec: StreamCodec<RegistryFriendlyByteBuf, BingoPayload<T>> = StreamCodec.of(
            { buf, payload -> converter.toPacketBuf(payload.value, PacketBufImpl(buf, itemStackFactory)) },
            { buf -> BingoPayload(type, converter.fromPacketBuf(PacketBufImpl(buf, itemStackFactory))) },
        )

        fun withServerHandler(handler: (BingoPayload<T>, IPayloadContext) -> Unit) = copy(serverHandler = handler)
        fun withClientHandler(handler: (BingoPayload<T>, IPayloadContext) -> Unit) = copy(clientHandler = handler)

        fun registerC2S(registrar: net.neoforged.neoforge.network.registration.PayloadRegistrar) {
            registrar.playToServer(type, codec) { payload, context ->
                serverHandler?.invoke(payload, context)
            }
        }

        fun registerS2C(registrar: net.neoforged.neoforge.network.registration.PayloadRegistrar) {
            registrar.playToClient(type, codec) { payload, context ->
                clientHandler?.invoke(payload, context)
            }
        }
    }
}
