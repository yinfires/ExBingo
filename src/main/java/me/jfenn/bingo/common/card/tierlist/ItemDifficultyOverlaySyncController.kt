package me.jfenn.bingo.common.card.tierlist

import me.jfenn.bingo.common.card.TagExpansionService
import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.data.ScopedData
import me.jfenn.bingo.common.event.packet.ServerPacketEvents
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.model.PlayerEvent
import me.jfenn.bingo.platform.event.model.ReloadEvent
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.Items

internal class ItemDifficultyOverlaySyncController(
    private val config: BingoConfig,
    private val server: MinecraftServer,
    private val data: ScopedData,
    private val tagExpansionService: TagExpansionService,
    private val packets: ServerPacketEvents,
    private val playerManager: IPlayerManager,
    eventBus: IEventBus,
) {
    private var packet = createPacket()

    init {
        eventBus.register(PlayerEvent.ChannelRegister) {
            send(it.player)
        }

        eventBus.register(ReloadEvent.After) {
            packet = createPacket()
            playerManager.getPlayers().forEach(::send)
        }
    }

    private fun send(player: IPlayerHandle) {
        packets.itemDifficultyOverlayV1.send(player, packet)
    }

    private fun createPacket(): ItemDifficultyOverlayPacket {
        val itemTiers = ItemDifficultyTierResolver.resolveItems(
            tierLists = data.tierLists,
            autoTierName = config.autoTier.tierListName,
            expandItem = tagExpansionService::expandItemTag,
        ).filterKeys(::isItemId)

        val advancementIds = server.advancements.allAdvancements
            .filter { it.value().display().isPresent }
            .map { it.id().toString() }
            .toSet()
        val advancementTiers = ItemDifficultyTierResolver.resolveAdvancements(
            tierLists = data.tierLists,
            autoTierName = config.autoTier.tierListName,
        ).filterKeys { it in advancementIds }

        return ItemDifficultyOverlayPacket(
            itemTiers = itemTiers,
            advancementTiers = advancementTiers,
        )
    }

    private fun isItemId(id: String): Boolean {
        val resource = ResourceLocation.tryParse(id) ?: return false
        return BuiltInRegistries.ITEM.get(resource) != Items.AIR || resource.path == "air"
    }
}
