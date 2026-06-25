package me.jfenn.bingo.common.card.autotier

import me.jfenn.bingo.common.card.tierlist.TierLabel

/**
 * Pure, engine-agnostic difficulty scorer used by auto-tiering.
 *
 * It assigns each objective a numeric difficulty "cost" derived from how hard it is to
 * obtain, then maps those costs onto the [TierLabel] tiers. Kept free of Minecraft types
 * so it can be unit-tested in isolation; [AutoTierService] feeds it data extracted from
 * the server.
 */
class AutoTierScorer(
    private val config: AutoTierConfig,
) {
    /** A craftable output and the ingredient item ids consumed by one recipe. */
    data class Recipe(
        val output: String,
        val ingredients: List<String>,
    )

    /** Describes how an item can be obtained, used to seed a base difficulty cost. */
    data class ItemSource(
        // recipes that produce this item (may be empty)
        val recipes: List<Recipe> = emptyList(),
        // base cost when the item is obtained directly (loot/mining/mob drop). null = no
        // direct source known, so the item is only obtainable via its recipes.
        val baseCost: Double? = null,
    )

    /** An advancement and the data needed to score it. */
    data class AdvancementInput(
        val id: String,
        // distance from the advancement tree root (root = 0)
        val depth: Int,
        // root category id, e.g. "minecraft:story" / "minecraft:end"
        val rootId: String?,
    )

    /**
     * Resolves the obtain-cost of every item via fixed-point relaxation.
     *
     * cost(item) = min(
     *   baseCost (if directly obtainable),
     *   for each recipe: recipeStepCost + max(cost(ingredient))   // depth dominates
     * )
     *
     * Recipes can form cycles; iterating to a fixed point handles that safely.
     */
    fun scoreItems(sources: Map<String, ItemSource>): Map<String, Double> {
        val unobtainable = Double.POSITIVE_INFINITY
        val cost = HashMap<String, Double>(sources.size * 2)

        // seed with direct (base) costs
        for ((item, source) in sources) {
            cost[item] = source.baseCost ?: unobtainable
        }

        // relax until stable (bounded by graph depth; cap to avoid pathological loops)
        val maxIterations = 64
        repeat(maxIterations) {
            var changed = false
            for ((item, source) in sources) {
                var best = cost[item] ?: unobtainable
                for (recipe in source.recipes) {
                    val ingredientMax = recipe.ingredients
                        .maxOfOrNull { cost[it] ?: unobtainable }
                        ?: 0.0
                    if (ingredientMax == unobtainable) continue
                    val candidate = config.recipeStepCost + ingredientMax
                    if (candidate < best) best = candidate
                }
                if (best < (cost[item] ?: unobtainable)) {
                    cost[item] = best
                    changed = true
                }
            }
            if (!changed) return@repeat
        }

        return cost.filterValues { it.isFinite() }
    }

    /** Scores an advancement from its tree depth and root category. */
    fun scoreAdvancement(input: AdvancementInput): Double {
        // root ids look like "minecraft:story/root" -> category is the first path segment
        val category = input.rootId
            ?.substringAfter(':')
            ?.substringBefore('/')
        val categoryWeight = when (category) {
            "story" -> 4.0
            "husbandry" -> 6.0
            "nether" -> 14.0
            "adventure" -> 16.0
            "end" -> 26.0
            else -> 10.0
        }
        // each step down the tree compounds the requirement
        return categoryWeight + input.depth * config.recipeStepCost * 2.0
    }

    /**
     * Maps scored objectives onto tiers.
     *
     * QUANTILE  - within each namespace, split sorted scores into 5 equal buckets so every
     *             mod fills all tiers regardless of its absolute difficulty range.
     * THRESHOLD - compare each score against the configured absolute cutoffs.
     */
    fun mapToTiers(scores: Map<String, Double>): Map<String, TierLabel> {
        if (scores.isEmpty()) return emptyMap()

        return when (config.mapping) {
            AutoTierConfig.Mapping.THRESHOLD -> scores.mapValues { (_, score) -> thresholdTier(score) }
            AutoTierConfig.Mapping.QUANTILE -> scores.entries
                .groupBy { it.key.substringBefore(':', missingDelimiterValue = "") }
                .flatMap { (_, entries) -> quantileTiers(entries.map { it.key to it.value }).entries }
                .associate { it.key to it.value }
        }
    }

    private fun thresholdTier(score: Double): TierLabel {
        // thresholds are ordered S..D; first cutoff the score meets wins
        val tiers = TierLabel.entries
        for ((i, cutoff) in config.thresholds.withIndex()) {
            if (score >= cutoff) return tiers.getOrElse(i) { TierLabel.D }
        }
        return TierLabel.D
    }

    private fun quantileTiers(scored: List<Pair<String, Double>>): Map<String, TierLabel> {
        // sort hardest-first; bucket 0 -> S (hardest) ... bucket 4 -> D (easiest)
        val sorted = scored.sortedByDescending { it.second }
        val tiers = TierLabel.entries
        val n = sorted.size
        return sorted.withIndex().associate { (index, pair) ->
            val bucket = (index * tiers.size) / n.coerceAtLeast(1)
            pair.first to tiers.getOrElse(bucket.coerceIn(0, tiers.size - 1)) { TierLabel.D }
        }
    }
}
