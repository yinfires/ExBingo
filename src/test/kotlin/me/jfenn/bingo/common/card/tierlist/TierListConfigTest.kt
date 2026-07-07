package me.jfenn.bingo.common.card.tierlist

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
}
