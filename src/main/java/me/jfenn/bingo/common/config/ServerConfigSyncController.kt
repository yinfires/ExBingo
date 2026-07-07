package me.jfenn.bingo.common.config

import me.jfenn.bingo.common.Permission
import me.jfenn.bingo.common.event.packet.ServerPacketEvents
import me.jfenn.bingo.integrations.permissions.IPermissionsApi
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.model.PlayerEvent
import org.slf4j.Logger

internal class ServerConfigSyncController(
    private val configService: ConfigService,
    private val eventBus: IEventBus,
    private val permissions: IPermissionsApi,
    private val packets: ServerPacketEvents,
    private val log: Logger,
) {
    private fun canEdit(player: IPlayerHandle): Boolean =
        permissions.hasPermission(player, Permission.CONFIGURE_SERVER)

    private fun snapshotFor(player: IPlayerHandle): ServerConfigSnapshotPacket {
        val editable = canEdit(player)
        return ServerConfigSnapshotPacket(
            config = if (editable) configService.config else BingoConfig(),
            canEdit = editable,
        )
    }

    private fun sendSnapshot(player: IPlayerHandle) {
        packets.serverConfigSnapshotV1.send(player, snapshotFor(player))
    }

    private fun updateConfig(player: IPlayerHandle, packet: ServerConfigUpdatePacket) {
        if (!canEdit(player)) {
            log.debug("[ServerConfigSyncController] Denying server config update from {} due to permissions", player)
            sendSnapshot(player)
            return
        }

        runCatching {
            val previousCommonValues = NeoForgeConfigBridge.snapshotCommonValues()
            try {
                NeoForgeConfigBridge.setCommonValuesFrom(packet.config)
                NeoForgeConfigBridge.applyCommonValuesTo(configService.config)
            } finally {
                NeoForgeConfigBridge.setCommonValuesFrom(previousCommonValues)
            }
        }.onSuccess { updatedConfig ->
            configService.writeConfig(updatedConfig)
            sendSnapshot(player)
        }.onFailure {
            log.warn("[ServerConfigSyncController] Ignoring invalid server config update from {}", player, it)
            sendSnapshot(player)
        }
    }

    init {
        eventBus.register(PlayerEvent.ChannelRegister) { event ->
            sendSnapshot(event.player)
        }

        eventBus.register(packets.serverConfigRequestV1) { event ->
            sendSnapshot(event.player)
        }

        eventBus.register(packets.serverConfigUpdateV1) { event ->
            updateConfig(event.player, event.packet)
        }
    }
}
