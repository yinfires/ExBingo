package me.jfenn.bingo.client.common.hud

import me.jfenn.bingo.client.platform.event.model.ClientReloadEvent
import me.jfenn.bingo.client.platform.event.model.ClientServerEvent
import me.jfenn.bingo.client.common.packet.ClientPacketEvents
import me.jfenn.bingo.common.card.tierlist.TierLabel
import me.jfenn.bingo.common.card.tierlist.TierListConfig
import me.jfenn.bingo.common.card.tierlist.ItemDifficultyTierResolver
import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.config.NeoForgeConfigBridge
import me.jfenn.bingo.common.data.TagLoader
import me.jfenn.bingo.common.data.TierListLoader
import me.jfenn.bingo.platform.event.IEventBus
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Items
import net.minecraft.world.item.ItemStack
import org.slf4j.Logger

internal class ItemDifficultyOverlayService(
    private val config: BingoConfig,
    private val tierListLoader: TierListLoader,
    private val tagLoader: TagLoader,
    private val packetEvents: ClientPacketEvents,
    eventBus: IEventBus,
    private val log: Logger,
) {
    @Volatile
    private var localItemTiers: Map<String, TierLabel> = emptyMap()

    @Volatile
    private var localAdvancementTiers: Map<String, TierLabel> = emptyMap()

    @Volatile
    private var serverItemTiers: Map<String, TierLabel>? = null

    @Volatile
    private var serverAdvancementTiers: Map<String, TierLabel>? = null

    init {
        eventBus.register(ClientReloadEvent) {
            reload(it.resourceManager)
        }

        eventBus.register(packetEvents.itemDifficultyOverlayV1) {
            serverItemTiers = it.packet.itemTiers
            serverAdvancementTiers = it.packet.advancementTiers
            log.info(
                "[ItemDifficultyOverlay] Received difficulty overlays for " +
                        "${it.packet.itemTiers.size} item(s), ${it.packet.advancementTiers.size} advancement(s)"
            )
        }

        eventBus.register(ClientServerEvent.Disconnect) {
            serverItemTiers = null
            serverAdvancementTiers = null
        }
    }

    fun getItemTier(stack: ItemStack): TierLabel? {
        if (!isEnabled() || stack.isEmpty) {
            return null
        }

        val itemId = BuiltInRegistries.ITEM.getKey(stack.item).toString()
        return (serverItemTiers ?: localItemTiers)[itemId]
    }

    fun getAdvancementTier(advancementId: String): TierLabel? {
        if (!isEnabled()) {
            return null
        }

        return (serverAdvancementTiers ?: localAdvancementTiers)[advancementId]
    }

    private fun reload(resourceManager: ResourceManager) {
        runCatching {
            val tags = tagLoader.loadTags(resourceManager)
            val tierLists = tierListLoader.loadTierLists(resourceManager)
            localItemTiers = buildItemTiers(tierLists, tags.mapValues { it.value.values })
            localAdvancementTiers = buildAdvancementTiers(tierLists)
            log.info(
                "[ItemDifficultyOverlay] Loaded local difficulty overlays for " +
                        "${localItemTiers.size} item(s), ${localAdvancementTiers.size} advancement(s)"
            )
        }.onFailure {
            localItemTiers = emptyMap()
            localAdvancementTiers = emptyMap()
            log.error("[ItemDifficultyOverlay] Unable to load difficulty overlays", it)
        }
    }

    private fun buildItemTiers(
        tierLists: Map<String, TierListConfig>,
        customTags: Map<String, Set<String>>,
    ): Map<String, TierLabel> {
        return ItemDifficultyTierResolver.resolveItems(
            tierLists = tierLists,
            autoTierName = config.autoTier.tierListName,
            expandItem = { expandItem(it, customTags, mutableSetOf()) },
        ).filterKeys(::isItemId)
    }

    private fun buildAdvancementTiers(
        tierLists: Map<String, TierListConfig>,
    ): Map<String, TierLabel> {
        return ItemDifficultyTierResolver.resolveAdvancements(
            tierLists = tierLists,
            autoTierName = config.autoTier.tierListName,
        ).filterKeys { !isItemId(it) || it.substringAfter(':', "").contains('/') }
    }

    fun isEnabled(): Boolean {
        return if (NeoForgeConfigBridge.clientSpec.isLoaded) {
            NeoForgeConfigBridge.clientValues.showItemDifficulties.get()
        } else {
            config.client.showItemDifficulties
        }
    }

    private fun isItemId(id: String): Boolean {
        val resource = ResourceLocation.tryParse(id) ?: return false
        return BuiltInRegistries.ITEM.get(resource) != Items.AIR || resource.path == "air"
    }

    private fun expandItem(
        item: String,
        customTags: Map<String, Set<String>>,
        visited: MutableSet<String>,
    ): Set<String> {
        if (!item.startsWith("#")) {
            return setOf(item)
        }

        if (!visited.add(item)) {
            return emptySet()
        }

        val tagId = item.substring(1)
        val registryItems = getRegistryItemTag(tagId)
        if (registryItems != null) {
            return registryItems
                .flatMap { expandItem(it, customTags, visited) }
                .toSet()
        }

        return customTags[tagId.substringAfter(':')]
            ?.flatMap { expandItem(it, customTags, visited) }
            ?.toSet()
            .orEmpty()
    }

    private fun getRegistryItemTag(id: String): List<String>? {
        val tag = try {
            TagKey.create(Registries.ITEM, ResourceLocation.parse(id))
        } catch (e: IllegalArgumentException) {
            return null
        }

        return BuiltInRegistries.ITEM.getTag(tag).orElse(null)
            ?.mapNotNull { holder -> holder.unwrapKey().orElse(null)?.location()?.toString() }
    }
}
