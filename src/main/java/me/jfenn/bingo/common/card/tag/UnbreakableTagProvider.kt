package me.jfenn.bingo.common.card.tag

import me.jfenn.bingo.common.card.filter.ObjectiveFilter
import me.jfenn.bingo.common.utils.cacheFor
import me.jfenn.bingo.common.utils.minutes
import me.jfenn.bingo.platform.item.IItemStackFactory
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger

/**
 * Produces the [ObjectiveFilter.UNBREAKABLE] tag, containing every item whose placed
 * block is unbreakable (destroy time < 0): bedrock, barrier, command/structure/jigsaw
 * blocks, the end portal frame's portal, light blocks, etc.
 *
 * The set only depends on the item registry, so it is cached for a long interval.
 */
internal class UnbreakableTagProvider(
    private val server: MinecraftServer,
    private val itemStackFactory: IItemStackFactory,
    private val log: Logger,
) : ITagProvider {
    private val cache = cacheFor(10.minutes) { _: Unit ->
        val items = itemStackFactory.listUnbreakableItems(server).toSet()
        log.info("[UnbreakableTagProvider] Found ${items.size} unbreakable-block items")
        mapOf(ObjectiveFilter.UNBREAKABLE to TagData(items))
    }

    override fun listTags(): Map<String, TagData> = cache.get(Unit)
}
