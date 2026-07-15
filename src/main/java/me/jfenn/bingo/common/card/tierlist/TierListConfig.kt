package me.jfenn.bingo.common.card.tierlist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.jfenn.bingo.common.card.TagExpansionService
import java.util.*
import kotlin.math.absoluteValue
import kotlin.random.Random

@Serializable
data class TierListConfig(
    val replace: Boolean = false,
    // Contains uncategorized entries
    val values: Set<TierListEntry> = emptySet(),
    @SerialName("S")
    val s: Set<TierListEntry> = emptySet(),
    @SerialName("A")
    val a: Set<TierListEntry> = emptySet(),
    @SerialName("B")
    val b: Set<TierListEntry> = emptySet(),
    @SerialName("C")
    val c: Set<TierListEntry> = emptySet(),
    @SerialName("D")
    val d: Set<TierListEntry> = emptySet(),
    val groups: List<Set<String>> = emptyList(),
) {

    @Transient
    var shouldValidate = true

    companion object {
        val EMPTY = TierListConfig(
            s = emptySet(),
            a = emptySet(),
            b = emptySet(),
            c = emptySet(),
            d = emptySet(),
            groups = emptyList(),
        )
    }

    fun isEmpty() = values.isEmpty() && s.isEmpty() && a.isEmpty() && b.isEmpty() && c.isEmpty() && d.isEmpty() && groups.none { it.isNotEmpty() }

    fun combine(other: TierListConfig): TierListConfig {
        if (other.replace) return other

        return this.copy(
            values = emptySet(),
            s = s + other.s,
            a = a + other.a,
            b = b + other.b,
            c = c + other.c,
            d = d + other.d,
            groups = groups + other.groups,
        ).let { list ->
            list.copy(
                values = (values + other.values)
                    .filterNot { list.isCategorized(it.item) }
                    .toSet()
            )
        }
    }

    fun plus(objective: String, tier: TierLabel?): TierListConfig {
        val entry = TierListEntry(null, objective)
        return this.mapTiers { set, setTier ->
            if (setTier == tier) set + entry else set - entry
        }.sort()
    }

    fun minus(objective: String): TierListConfig {
        val entry = TierListEntry(null, objective)
        return this.mapTiers { set, _ -> set - entry }.sort()
    }

    fun getTier(tier: TierLabel) = when (tier) {
        TierLabel.S -> s
        TierLabel.A -> a
        TierLabel.B -> b
        TierLabel.C -> c
        TierLabel.D -> d
    }

    private fun getWeightedSet(
        set: Set<TierListEntry>,
        sourceWeights: Map<String, Double>,
    ): LinkedList<Pair<TierListEntry, Double>> {
        val ret = LinkedList<Pair<TierListEntry, Double>>()
        for (entry in set) {
            val inverseWeight = groups
                .filter { group -> group.contains(entry.item) }
                .sumOf { group -> group.size }
                .coerceAtLeast(1)

            val configuredWeight = sourceWeights[entry.listName] ?: 1.0
            val sourceWeight = configuredWeight
                .takeIf { it.isFinite() }
                ?.coerceAtLeast(0.0)
                ?: 0.0
            val weight = sourceWeight / inverseWeight
            if (weight > 0.0) {
                ret.add(entry to weight)
            }
        }

        return ret
    }

    private fun pickFromTier(
        tier: TierLabel,
        excluding: Set<String>,
        random: Random,
        sourceWeights: Map<String, Double>,
    ) = sequence {
        val items = getTier(tier)
        val itemWeights = getWeightedSet(items, sourceWeights)

        getWeightedSet(values, sourceWeights)
            // divide by 5, since values are placed in each tier
            .map { (entry, weight) -> entry to weight / 5.0 }
            .let { itemWeights.addAll(it) }

        while (itemWeights.isNotEmpty()) {
            val itemWeightTotal = itemWeights.sumOf { it.second }
            val choice = random.nextDouble(0.0, itemWeightTotal)

            // get the index of the random weight in the tier list
            var i = 0
            var itemWeightSum = 0.0
            for ((_, weight) in itemWeights) {
                itemWeightSum += weight
                if (itemWeightSum >= choice)
                    break

                i++
            }

            i = i.coerceAtMost(itemWeights.size - 1)
            val (item, _) = itemWeights.removeAt(i)
            // skip excluded items
            if (excluding.contains(item.item)) continue

            // add the item to the list
            yield(item)
        }
    }

    fun pick(
        tier: TierLabel,
        excluding: Set<String>,
        random: Random,
        sourceWeights: Map<String, Double> = emptyMap(),
    ) = sequence {
        // Sort tiers by distance from the selected tier
        // (i.e. pick tier first, and then any items from available adjacent tiers)
        val sortedTiers = TierLabel.entries.sortedBy { (it.ordinal - tier.ordinal).absoluteValue }
        for (sortedTier in sortedTiers) {
            yieldAll(pickFromTier(sortedTier, excluding, random, sourceWeights))
        }
    }

    fun contains(objectiveId: String): Boolean {
        return isCategorized(objectiveId) || values.any { it.item == objectiveId }
    }

    fun isCategorized(objectiveId: String): Boolean {
        return s.any { it.item == objectiveId }
                || a.any { it.item == objectiveId }
                || b.any { it.item == objectiveId }
                || c.any { it.item == objectiveId }
                || d.any { it.item == objectiveId }
    }

    private fun mapTiers(map: (Set<TierListEntry>, tier: TierLabel?) -> Set<TierListEntry>): TierListConfig {
        return this.copy(
            values = map(this.values, null),
            s = map(this.s, TierLabel.S),
            a = map(this.a, TierLabel.A),
            b = map(this.b, TierLabel.B),
            c = map(this.c, TierLabel.C),
            d = map(this.d, TierLabel.D),
        )
    }

    fun sort(): TierListConfig = this.copy(
        values = values.sorted().toSet(),
        s = s.sorted().toSet(),
        a = a.sorted().toSet(),
        b = b.sorted().toSet(),
        c = c.sorted().toSet(),
        d = d.sorted().toSet(),
    )

    fun allEntries(): Set<TierListEntry> = values + s + a + b + c + d

    internal fun expandTags(
        expansionService: TagExpansionService,
    ) : TierListConfig {
        return this
            .mapTiers { tier, _ ->
                tier.flatMap { expandTags(expansionService, it) }.toSet()
            }
            .copy(
                groups = this.groups.map { expansionService.expandItemTags(it) }
            )
    }

    private fun expandTags(
        expansionService: TagExpansionService,
        entry: TierListEntry,
    ): List<TierListEntry> {
        return expansionService.expandItemTag(entry.item)
            .map { TierListEntry(null, it) }
    }

    fun expandName(
        listName: String,
    ) : TierListConfig {
        return this
            .mapTiers { tier, label ->
                tier
                    .onEach {
                        it.listName = listName
                        it.tierLabel = label
                    }
                    .toSet()
            }
    }

    fun filter(predicate: (TierListEntry) -> Boolean): TierListConfig {
        return mapTiers { entries, _ ->
            entries.filter(predicate).toSet()
        }
    }

}
