package me.jfenn.bingo.common.card.tierlist

import kotlin.test.Test
import kotlin.test.assertEquals

class ItemDifficultyTierResolverTest {
    @Test
    fun `non auto tiers override auto tiers`() {
        val tiers = ItemDifficultyTierResolver.resolveItems(
            tierLists = mapOf(
                "items" to TierListConfig(b = setOf(TierListEntry(null, "minecraft:diamond"))),
                "autotier" to TierListConfig(s = setOf(TierListEntry(null, "minecraft:diamond"))),
            ),
            autoTierName = "autotier",
            expandItem = { setOf(it) },
        )

        assertEquals(TierLabel.B, tiers["minecraft:diamond"])
    }

    @Test
    fun `same priority conflicts choose harder tier and expand tags`() {
        val tiers = ItemDifficultyTierResolver.resolveItems(
            tierLists = mapOf(
                "items" to TierListConfig(
                    a = setOf(TierListEntry(null, "#minecraft:gems")),
                    c = setOf(TierListEntry(null, "minecraft:diamond")),
                ),
            ),
            autoTierName = "autotier",
            expandItem = { if (it == "#minecraft:gems") setOf("minecraft:diamond") else setOf(it) },
        )

        assertEquals(TierLabel.A, tiers["minecraft:diamond"])
    }

    @Test
    fun `advancement tiers stay separate from typed item tiers`() {
        val itemTiers = ItemDifficultyTierResolver.resolveItems(
            tierLists = mapOf(
                "items" to TierListConfig(
                    s = setOf(TierListEntry("advancement", "example:root")),
                    d = setOf(TierListEntry("item", "example:root")),
                ),
            ),
            autoTierName = "autotier",
            expandItem = { setOf(it) },
        )
        val advancementTiers = ItemDifficultyTierResolver.resolveAdvancements(
            tierLists = mapOf(
                "items" to TierListConfig(
                    s = setOf(TierListEntry("advancement", "example:root")),
                    d = setOf(TierListEntry("item", "example:root")),
                ),
            ),
            autoTierName = "autotier",
        )

        assertEquals(TierLabel.D, itemTiers["example:root"])
        assertEquals(TierLabel.S, advancementTiers["example:root"])
    }
}
