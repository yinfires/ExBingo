package me.jfenn.bingo.common.card.tierlist

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.jfenn.bingo.common.card.TagExpansionService
import me.jfenn.bingo.common.data.ScopedData
import me.jfenn.bingo.common.utils.json
import me.jfenn.bingo.platform.IRegistryEntry
import me.jfenn.bingo.platform.ITagAccessor
import me.jfenn.bingo.platform.ITagContents
import org.slf4j.helpers.NOPLogger
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class TierListConfigTest {
    @Test
    fun `source weight zero removes source from weighted picks`() {
        val disabled = TierListEntry(null, "mod:disabled").apply { listName = "disabled_source" }
        val enabled = TierListEntry(null, "mod:enabled").apply { listName = "enabled_source" }
        val tierList = TierListConfig(s = setOf(disabled, enabled))

        val picked = tierList.pick(
            tier = TierLabel.S,
            excluding = emptySet(),
            random = Random(0),
            sourceWeights = mapOf("disabled_source" to 0.0),
        ).toList()

        assertEquals(listOf("mod:enabled"), picked.map { it.item })
    }

    @Test
    fun `missing source weight defaults to one`() {
        val entry = TierListEntry(null, "mod:default").apply { listName = "default_source" }
        val tierList = TierListConfig(s = setOf(entry))

        val picked = tierList.pick(
            tier = TierLabel.S,
            excluding = emptySet(),
            random = Random(0),
            sourceWeights = emptyMap(),
        ).toList()

        assertEquals(listOf("mod:default"), picked.map { it.item })
    }

    @Test
    fun `typed entries keep item and advancement ids separate`() {
        val item = TierListEntry("item", "example:same_id")
        val advancement = TierListEntry("advancement", "example:same_id")

        val tierList = TierListConfig(
            s = setOf(item, advancement),
        )

        assertEquals(setOf(item, advancement), tierList.s)
        assertEquals("item!example:same_id", item.typedId)
        assertEquals("advancement!example:same_id", advancement.typedId)
    }

    @Test
    fun `typed entries count as categorized by bare objective id`() {
        val tierList = TierListConfig(
            s = setOf(TierListEntry("advancement", "example:same_id")),
            values = setOf(TierListEntry("item", "example:value_id")),
        )

        assertEquals(true, tierList.isCategorized("example:same_id"))
        assertEquals(true, tierList.contains("example:same_id"))
        assertEquals(true, tierList.contains("example:value_id"))
    }

    @Test
    fun `typed entries round trip with type prefixes`() {
        val tierList = TierListConfig(
            s = setOf(
                TierListEntry("item", "example:same_id"),
                TierListEntry("advancement", "example:same_id"),
            ),
        )

        val encoded = json.encodeToString(tierList)
        val decoded = json.decodeFromString<TierListConfig>(encoded)

        assertEquals(tierList.s, decoded.s)
        assertEquals(true, encoded.contains("item!example:same_id"))
        assertEquals(true, encoded.contains("advancement!example:same_id"))
    }

    @Test
    fun `tag expansion preserves typed entries`() {
        val tierList = TierListConfig(
            a = setOf(
                TierListEntry("item", "create:sturdy_sheet"),
                TierListEntry("advancement", "create:sturdy_sheet"),
                TierListEntry("item", "#create:sheets"),
                TierListEntry("advancement", "#create:sheets"),
            ),
        )

        val expanded = tierList.expandTags(tagExpansionService())

        assertEquals(
            setOf(
                TierListEntry("item", "create:sturdy_sheet"),
                TierListEntry("advancement", "create:sturdy_sheet"),
                TierListEntry("item", "create:copper_sheet"),
                TierListEntry("item", "create:iron_sheet"),
                TierListEntry("advancement", "#create:sheets"),
            ),
            expanded.a
        )
    }

    private fun tagExpansionService(): TagExpansionService {
        return TagExpansionService(
            log = NOPLogger.NOP_LOGGER,
            tagAccessor = FakeTagAccessor,
            data = ScopedData(),
        )
    }

    private object FakeTagAccessor : ITagAccessor {
        override fun getItemTag(id: String): List<String>? {
            return when (id) {
                "create:sheets" -> listOf("create:copper_sheet", "create:iron_sheet")
                else -> null
            }
        }

        override fun getBlockTag(id: String): ITagContents<IRegistryEntry.Block> = emptyTagContents()

        override fun getBiomeTag(id: String): ITagContents<IRegistryEntry.Biome> = emptyTagContents()

        private fun <T : IRegistryEntry> emptyTagContents(): ITagContents<T> {
            return object : ITagContents<T> {
                override fun list(): List<T> = emptyList()
                override fun contains(entry: T): Boolean = false
            }
        }
    }
}
