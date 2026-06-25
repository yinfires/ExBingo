package me.jfenn.bingo.common.card.autotier

import me.jfenn.bingo.common.card.tierlist.TierLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutoTierScorerTest {

    private val scorer = AutoTierScorer(AutoTierConfig(recipeStepCost = 2.0))

    @Test
    fun `directly obtainable items cost their base cost`() {
        val sources = mapOf(
            "mod:ore" to AutoTierScorer.ItemSource(baseCost = 1.0),
        )
        val scores = scorer.scoreItems(sources)
        assertEquals(1.0, scores["mod:ore"])
    }

    @Test
    fun `crafting depth increases cost`() {
        val sources = mapOf(
            "mod:raw" to AutoTierScorer.ItemSource(baseCost = 1.0),
            "mod:ingot" to AutoTierScorer.ItemSource(
                recipes = listOf(AutoTierScorer.Recipe("mod:ingot", listOf("mod:raw"))),
            ),
            "mod:tool" to AutoTierScorer.ItemSource(
                recipes = listOf(AutoTierScorer.Recipe("mod:tool", listOf("mod:ingot"))),
            ),
        )
        val scores = scorer.scoreItems(sources)
        // raw < ingot < tool (each crafting step adds recipeStepCost)
        assertTrue(scores.getValue("mod:raw") < scores.getValue("mod:ingot"))
        assertTrue(scores.getValue("mod:ingot") < scores.getValue("mod:tool"))
        assertEquals(3.0, scores["mod:ingot"]) // 2.0 step + 1.0 ingredient
        assertEquals(5.0, scores["mod:tool"])  // 2.0 step + 3.0 ingredient
    }

    @Test
    fun `recipe cycles do not loop forever and resolve to obtainable cost`() {
        // a <-> b cycle, but a is also directly obtainable
        val sources = mapOf(
            "mod:a" to AutoTierScorer.ItemSource(
                recipes = listOf(AutoTierScorer.Recipe("mod:a", listOf("mod:b"))),
                baseCost = 1.0,
            ),
            "mod:b" to AutoTierScorer.ItemSource(
                recipes = listOf(AutoTierScorer.Recipe("mod:b", listOf("mod:a"))),
            ),
        )
        val scores = scorer.scoreItems(sources)
        assertEquals(1.0, scores["mod:a"])
        assertEquals(3.0, scores["mod:b"]) // 2.0 step + 1.0 (a)
    }

    @Test
    fun `purely unobtainable items are dropped`() {
        val sources = mapOf(
            // only craftable from something with no source -> never resolves
            "mod:phantom" to AutoTierScorer.ItemSource(
                recipes = listOf(AutoTierScorer.Recipe("mod:phantom", listOf("mod:missing"))),
            ),
            "mod:missing" to AutoTierScorer.ItemSource(),
        )
        val scores = scorer.scoreItems(sources)
        assertTrue(scores.isEmpty())
    }

    @Test
    fun `quantile mapping fills all tiers per namespace`() {
        // 5 items with strictly increasing scores -> one per tier
        val scores = (0 until 5).associate { "mod:item$it" to it.toDouble() }
        val tiers = AutoTierScorer(AutoTierConfig(mapping = AutoTierConfig.Mapping.QUANTILE))
            .mapToTiers(scores)

        assertEquals(5, tiers.values.toSet().size)
        // hardest (highest score) -> S, easiest -> D
        assertEquals(TierLabel.S, tiers["mod:item4"])
        assertEquals(TierLabel.D, tiers["mod:item0"])
    }

    @Test
    fun `quantile mapping is independent per namespace`() {
        // two namespaces, each with its own range; each should fill its own tiers
        val scores = buildMap {
            (0 until 5).forEach { put("a:item$it", it.toDouble()) }
            (0 until 5).forEach { put("b:item$it", (it * 100).toDouble()) }
        }
        val tiers = AutoTierScorer(AutoTierConfig(mapping = AutoTierConfig.Mapping.QUANTILE))
            .mapToTiers(scores)
        // namespace a's hardest is S even though its scores are tiny vs namespace b
        assertEquals(TierLabel.S, tiers["a:item4"])
        assertEquals(TierLabel.D, tiers["a:item0"])
        assertEquals(TierLabel.S, tiers["b:item4"])
        assertEquals(TierLabel.D, tiers["b:item0"])
    }

    @Test
    fun `threshold mapping uses absolute cutoffs`() {
        val config = AutoTierConfig(
            mapping = AutoTierConfig.Mapping.THRESHOLD,
            thresholds = listOf(40.0, 25.0, 14.0, 6.0, 0.0),
        )
        val tiers = AutoTierScorer(config).mapToTiers(
            mapOf(
                "mod:hard" to 50.0,
                "mod:easy" to 2.0,
            )
        )
        assertEquals(TierLabel.S, tiers["mod:hard"])
        assertEquals(TierLabel.D, tiers["mod:easy"])
    }

    @Test
    fun `advancement depth and category affect score`() {
        val shallow = scorer.scoreAdvancement(
            AutoTierScorer.AdvancementInput("mod:a", depth = 0, rootId = "minecraft:story")
        )
        val deep = scorer.scoreAdvancement(
            AutoTierScorer.AdvancementInput("mod:b", depth = 5, rootId = "minecraft:story")
        )
        val endgame = scorer.scoreAdvancement(
            AutoTierScorer.AdvancementInput("mod:c", depth = 0, rootId = "minecraft:end")
        )
        assertTrue(deep > shallow)
        assertTrue(endgame > shallow)
    }
}
