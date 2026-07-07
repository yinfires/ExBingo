package me.jfenn.bingo.common.card.tierlist

internal object ItemDifficultyTierResolver {
    fun resolveItems(
        tierLists: Map<String, TierListConfig>,
        autoTierName: String,
        expandItem: (String) -> Set<String>,
    ): Map<String, TierLabel> {
        return resolve(tierLists, autoTierName) { entry ->
            when (entry.type) {
                null, "item" -> expandItem(entry.item)
                else -> emptySet()
            }
        }
    }

    fun resolveAdvancements(
        tierLists: Map<String, TierListConfig>,
        autoTierName: String,
    ): Map<String, TierLabel> {
        return resolve(tierLists, autoTierName) { entry ->
            when (entry.type) {
                null, "advancement" -> setOf(entry.item)
                else -> emptySet()
            }
        }
    }

    private fun resolve(
        tierLists: Map<String, TierListConfig>,
        autoTierName: String,
        expandEntry: (TierListEntry) -> Set<String>,
    ): Map<String, TierLabel> {
        val baseTiers = mutableMapOf<String, TierLabel>()
        val autoTiers = mutableMapOf<String, TierLabel>()

        tierLists
            .filterKeys { it != autoTierName }
            .values
            .forEach { collectTiers(it, expandEntry, baseTiers) }

        tierLists[autoTierName]?.let { collectTiers(it, expandEntry, autoTiers) }

        return autoTiers
            .filterKeys { it !in baseTiers }
            .plus(baseTiers)
    }

    private fun collectTiers(
        tierList: TierListConfig,
        expandEntry: (TierListEntry) -> Set<String>,
        output: MutableMap<String, TierLabel>,
    ) {
        for (tier in TierLabel.entries) {
            tierList.getTier(tier)
                .flatMap(expandEntry)
                .forEach { objectiveId ->
                    output.merge(objectiveId, tier) { previous, next ->
                        if (next.ordinal < previous.ordinal) next else previous
                    }
                }
        }
    }
}
