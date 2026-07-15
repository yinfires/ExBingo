package me.jfenn.bingo.common.card.tierlist

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.jfenn.bingo.common.utils.json
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
}
