package me.jfenn.bingo.client.integrations.xaero

import me.jfenn.bingo.client.common.event.ClientGameEndEvent
import me.jfenn.bingo.client.common.event.ClientGameResetEvent
import me.jfenn.bingo.client.common.packet.ClientPacketEvents
import me.jfenn.bingo.client.platform.ClientPacket
import me.jfenn.bingo.client.platform.event.model.ClientServerEvent
import me.jfenn.bingo.common.ready.ReadyUpdatePacket
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.platform.event.IEventBus

internal class XaeroCacheCleanupState(
    eventBus: IEventBus,
    packetEvents: ClientPacketEvents,
) : BingoComponent() {
    private var shouldCleanOnDisconnect = false

    init {
        eventBus.register(packetEvents.readyUpdateV1, ::onReadyUpdate)
        eventBus.register(packetEvents.readyUpdateV2, ::onReadyUpdate)
        eventBus.register(packetEvents.readyUpdateV3, ::onReadyUpdate)

        eventBus.register(ClientGameEndEvent) {
            shouldCleanOnDisconnect = false
        }

        eventBus.register(ClientGameResetEvent) {
            shouldCleanOnDisconnect = true
        }

        eventBus.register(ClientServerEvent.Join) {
            shouldCleanOnDisconnect = false
        }
    }

    private fun onReadyUpdate(event: ClientPacket<ReadyUpdatePacket>) {
        shouldCleanOnDisconnect = event.packet.state == GameState.PREGAME
    }

    fun consumeDisconnectCleanupRequest(): Boolean {
        val shouldClean = shouldCleanOnDisconnect
        shouldCleanOnDisconnect = false
        return shouldClean
    }
}
