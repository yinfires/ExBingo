package me.jfenn.bingo.client.common.config

import me.jfenn.bingo.client.common.packet.ClientPacketEvents
import me.jfenn.bingo.client.platform.event.model.ClientServerEvent
import me.jfenn.bingo.common.config.ServerConfigRequestPacket
import me.jfenn.bingo.common.config.ServerConfigSnapshotPacket
import me.jfenn.bingo.common.config.ServerConfigUpdatePacket
import me.jfenn.bingo.platform.event.IEventBus

internal class ClientServerConfigController(
    private val packets: ClientPacketEvents,
    private val eventBus: IEventBus,
) {
    fun requestSnapshot() {
        packets.serverConfigRequestV1.send(ServerConfigRequestPacket)
    }

    fun sendUpdate(packet: ServerConfigUpdatePacket) {
        packets.serverConfigUpdateV1.send(packet)
    }

    init {
        eventBus.register(packets.serverConfigSnapshotV1) { event ->
            ClientServerConfigState.update(event.packet)
        }

        eventBus.register(ClientServerEvent.ChannelRegister) {
            requestSnapshot()
        }

        eventBus.register(ClientServerEvent.Disconnect) {
            ClientServerConfigState.clear()
        }
    }
}

object ClientServerConfigState {
    @Volatile
    private var currentSnapshot: ServerConfigSnapshotPacket? = null

    @Volatile
    var revision: Long = 0
        private set

    val snapshot: ServerConfigSnapshotPacket?
        get() = currentSnapshot

    fun update(snapshot: ServerConfigSnapshotPacket) {
        currentSnapshot = snapshot
        revision++
    }

    fun clear() {
        currentSnapshot = null
        revision++
    }
}
