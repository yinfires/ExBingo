package me.jfenn.bingo.client.integrations.xaero

import me.jfenn.bingo.client.common.event.ClientGameEndEvent
import me.jfenn.bingo.client.common.event.ClientGameResetEvent
import me.jfenn.bingo.client.common.packet.ClientPacketEvents
import me.jfenn.bingo.client.platform.ClientPacket
import me.jfenn.bingo.client.platform.IClientNetworking
import me.jfenn.bingo.client.platform.IClientPacketHandlerC2S
import me.jfenn.bingo.client.platform.IClientPacketHandlerS2C
import me.jfenn.bingo.client.platform.event.model.ClientServerEvent
import me.jfenn.bingo.common.ready.ReadyUpdatePacket
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.platform.event.ICallbackHandle
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.IReturnEvent
import me.jfenn.bingo.platform.packet.PacketConverter
import java.time.Duration
import java.util.function.Function
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XaeroCacheCleanupStateTest {
    @Test
    fun `disconnect cleanup is requested only after returning to lobby`() {
        val eventBus = RecordingEventBus()
        val packetEvents = ClientPacketEvents(FakeClientNetworking)
        val state = XaeroCacheCleanupState(eventBus, packetEvents)

        assertFalse(state.consumeDisconnectCleanupRequest())

        eventBus.emit(packetEvents.readyUpdateV3, readyUpdate(GameState.PREGAME))

        assertTrue(state.consumeDisconnectCleanupRequest())
        assertFalse(state.consumeDisconnectCleanupRequest())
    }

    @Test
    fun `active game state disables pending cleanup`() {
        val eventBus = RecordingEventBus()
        val packetEvents = ClientPacketEvents(FakeClientNetworking)
        val state = XaeroCacheCleanupState(eventBus, packetEvents)

        eventBus.emit(packetEvents.readyUpdateV3, readyUpdate(GameState.PREGAME))
        eventBus.emit(packetEvents.readyUpdateV3, readyUpdate(GameState.PLAYING))

        assertFalse(state.consumeDisconnectCleanupRequest())
    }

    @Test
    fun `game end disables pending lobby cleanup until reset finishes`() {
        val eventBus = RecordingEventBus()
        val packetEvents = ClientPacketEvents(FakeClientNetworking)
        val state = XaeroCacheCleanupState(eventBus, packetEvents)

        eventBus.emit(packetEvents.readyUpdateV3, readyUpdate(GameState.PREGAME))
        eventBus.emit(ClientGameEndEvent, Unit)

        assertFalse(state.consumeDisconnectCleanupRequest())
    }

    @Test
    fun `client reset event requests cleanup before next lobby packet arrives`() {
        val eventBus = RecordingEventBus()
        val packetEvents = ClientPacketEvents(FakeClientNetworking)
        val state = XaeroCacheCleanupState(eventBus, packetEvents)

        eventBus.emit(ClientGameResetEvent, Unit)

        assertTrue(state.consumeDisconnectCleanupRequest())
    }

    @Test
    fun `joining clears a pending cleanup request`() {
        val eventBus = RecordingEventBus()
        val packetEvents = ClientPacketEvents(FakeClientNetworking)
        val state = XaeroCacheCleanupState(eventBus, packetEvents)

        eventBus.emit(packetEvents.readyUpdateV3, readyUpdate(GameState.PREGAME))
        eventBus.emit(ClientServerEvent.Join, ClientServerEvent())

        assertFalse(state.consumeDisconnectCleanupRequest())
    }

    private fun readyUpdate(state: GameState) = ClientPacket(
        ReadyUpdatePacket(
            isRunning = false,
            isReady = false,
            state = state,
            remainingDuration = Duration.ZERO,
            totalDuration = Duration.ZERO,
            readyPlayers = 0,
            totalPlayers = 0,
            title = null,
            subtitle = null,
        )
    )

    private object FakeClientNetworking : IClientNetworking {
        override fun <T> registerC2S(converter: PacketConverter<T>): IClientPacketHandlerC2S<T> {
            return object : IClientPacketHandlerC2S<T> {
                override fun isSupported(): Boolean = true

                override fun send(packet: T): Boolean = true
            }
        }

        override fun <T> registerS2C(converter: PacketConverter<T>): IClientPacketHandlerS2C<T> {
            return object : IClientPacketHandlerS2C<T> {}
        }
    }

    private class RecordingEventBus : IEventBus {
        private val callbacks = mutableMapOf<IReturnEvent<*, *>, MutableList<Function<*, *>>>()

        override fun <T : Any, R> register(type: IReturnEvent<T, R>, callback: Function<T, R>): ICallbackHandle {
            callbacks.getOrPut(type) { mutableListOf() } += callback
            return object : ICallbackHandle {
                override fun close() {
                    callbacks[type]?.remove(callback)
                }
            }
        }

        override fun <T : Any, R> emit(type: IReturnEvent<T, R>, event: T): List<R> {
            val handlers = callbacks[type].orEmpty()
            return handlers.map { handler ->
                @Suppress("UNCHECKED_CAST")
                (handler as Function<T, R>).apply(event)
            }
        }
    }
}
