package me.jfenn.bingo.impl.networking

import me.jfenn.bingo.client.platform.ClientPacket
import me.jfenn.bingo.client.platform.IClientPacketHandlerS2C
import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.platform.event.ICallbackHandle
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.IReturnEvent
import me.jfenn.bingo.platform.item.IFilledMap
import me.jfenn.bingo.platform.item.IFireworkRocket
import me.jfenn.bingo.platform.item.IItemStack
import me.jfenn.bingo.platform.item.IItemStackFactory
import me.jfenn.bingo.platform.item.IPlayerHead
import me.jfenn.bingo.platform.item.IWrittenBook
import me.jfenn.bingo.platform.packet.IServerPacketHandlerC2S
import me.jfenn.bingo.platform.packet.PacketConverter
import me.jfenn.bingo.platform.packet.ServerPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import java.util.function.Function
import kotlin.test.Test
import kotlin.test.assertTrue

class NeoForgePacketRegistryTest {
    @Test
    fun `custom payload registry records server and client converter ids by direction`() {
        val c2sId = ResourceLocation.fromNamespaceAndPath("exbingo", "registry_snapshot_test_c2s")
        val s2cId = ResourceLocation.fromNamespaceAndPath("exbingo", "registry_snapshot_test_s2c")
        val clientC2SId = ResourceLocation.fromNamespaceAndPath("exbingo", "registry_snapshot_test_client_c2s")
        val clientS2CId = ResourceLocation.fromNamespaceAndPath("exbingo", "registry_snapshot_test_client_s2c")
        val bidirectionalId = ResourceLocation.fromNamespaceAndPath("exbingo", "registry_snapshot_test_bidirectional")

        NeoForgePacketRegistry.registerC2S(StringConverter(c2sId), UnusedItemStackFactory, NoopEventBus, TestC2SEvent)
        NeoForgePacketRegistry.registerS2C(StringConverter(s2cId), UnusedItemStackFactory)
        NeoForgePacketRegistry.registerClientC2S(StringConverter(clientC2SId), UnusedItemStackFactory)
        NeoForgePacketRegistry.registerClientS2C(StringConverter(clientS2CId), UnusedItemStackFactory, NoopEventBus, TestS2CEvent)
        NeoForgePacketRegistry.registerC2S(StringConverter(bidirectionalId), UnusedItemStackFactory, NoopEventBus, TestC2SEvent)
        NeoForgePacketRegistry.registerClientS2C(StringConverter(bidirectionalId), UnusedItemStackFactory, NoopEventBus, TestS2CEvent)

        assertTrue(c2sId in NeoForgePacketRegistry.registeredC2SIds())
        assertTrue(clientC2SId in NeoForgePacketRegistry.registeredC2SIds())
        assertTrue(bidirectionalId in NeoForgePacketRegistry.registeredC2SIds())
        assertTrue(s2cId in NeoForgePacketRegistry.registeredS2CIds())
        assertTrue(clientS2CId in NeoForgePacketRegistry.registeredS2CIds())
        assertTrue(bidirectionalId in NeoForgePacketRegistry.registeredS2CIds())
    }

    private class StringConverter(
        override val id: ResourceLocation,
    ) : PacketConverter<String> {
        override fun toPacketBuf(source: String, dest: IPacketBuf) {
            dest.writeString(source)
        }

        override fun fromPacketBuf(buf: IPacketBuf): String {
            return buf.readString()
        }
    }

    private object TestC2SEvent : IServerPacketHandlerC2S<String>

    private object TestS2CEvent : IClientPacketHandlerS2C<String>

    private object NoopEventBus : IEventBus {
        override fun <T : Any, R> register(
            type: IReturnEvent<T, R>,
            callback: Function<T, R>,
        ): ICallbackHandle {
            return object : ICallbackHandle {
                override fun close() = kotlin.Unit
            }
        }

        override fun <T : Any, R> emit(type: IReturnEvent<T, R>, event: T): List<R> {
            return emptyList()
        }
    }

    private object UnusedItemStackFactory : IItemStackFactory {
        override val emptyStack: IItemStack
            get() = error("unused")

        override fun listItems(server: MinecraftServer): List<String> = error("unused")
        override fun listUnbreakableItems(server: MinecraftServer): List<String> = error("unused")
        override fun isEnabledInWorld(item: String, server: MinecraftServer): Boolean = error("unused")
        override fun createStack(item: String, count: Int): IItemStack = error("unused")
        override fun createStack(item: ResourceLocation, count: Int): IItemStack = error("unused")
        override fun createStack(item: Item, count: Int): IItemStack = error("unused")
        override fun forStack(stack: ItemStack?): IItemStack = error("unused")
        override fun createFilledMap(): IFilledMap = error("unused")
        override fun createFireworkRocket(): IFireworkRocket = error("unused")
        override fun createWrittenBook(): IWrittenBook = error("unused")
        override fun createPlayerHead(): IPlayerHead = error("unused")
    }
}
