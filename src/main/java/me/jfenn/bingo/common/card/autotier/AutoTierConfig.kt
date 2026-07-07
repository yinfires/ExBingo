package me.jfenn.bingo.common.card.autotier

import kotlinx.serialization.Serializable
import me.jfenn.bingo.common.MOD_ID_BINGO
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.common.TranslatableEnum

/**
 * Configuration for the `/bingo autotier` auto-classification feature.
 *
 * Auto-tiering only ever assigns a difficulty tier to objectives that are
 * **uncategorized** and **non-vanilla**. It never touches:
 *  - vanilla (`minecraft:`) content,
 *  - objectives already placed in any loaded tier list (including mod-provided ones),
 *  - objectives already assigned by a previous auto-tier run,
 *  - objectives tagged `unobtainable` or `unbreakable`.
 */
@Serializable
data class AutoTierConfig(
    // name of the tier list file auto-tiering writes to (config/exbingo/tierlists/<name>.tierlist.json)
    val tierListName: String = "autotier",

    // how scored objectives are mapped onto the S/A/B/C/D tiers.
    //  QUANTILE  - split each namespace's scored objectives into 5 equal-sized buckets,
    //              so every mod naturally fills all five tiers (recommended).
    //  THRESHOLD - use the absolute score cutoffs in `thresholds` below.
    val mapping: Mapping = Mapping.QUANTILE,

    // absolute score cutoffs used when mapping == THRESHOLD.
    // an objective with score >= thresholds[i] lands in tier order i (S=0 .. D=4),
    // evaluated from S downward.
    val thresholds: List<Double> = listOf(40.0, 25.0, 14.0, 6.0, 0.0),

    // per-recipe-step cost added on top of ingredient costs (controls how fast
    // crafting depth escalates difficulty).
    val recipeStepCost: Double = 2.0,
) {
    enum class Mapping(private val translationKey: String) : TranslatableEnum {
        QUANTILE("$MOD_ID_BINGO.configuration.common.auto_tier.mapping.quantile"),
        THRESHOLD("$MOD_ID_BINGO.configuration.common.auto_tier.mapping.threshold"),
        ;

        override fun getTranslatedName(): Component = Component.translatable(translationKey)
    }
}
