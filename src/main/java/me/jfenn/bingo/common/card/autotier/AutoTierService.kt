package me.jfenn.bingo.common.card.autotier

import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.MOD_ID_MINECRAFT
import me.jfenn.bingo.common.card.CardGeneratorState
import me.jfenn.bingo.common.card.filter.ObjectiveFilter
import me.jfenn.bingo.common.card.objective.BingoObjective
import me.jfenn.bingo.common.card.objective.BingoObjectiveManager
import me.jfenn.bingo.common.card.tag.TagService
import me.jfenn.bingo.common.card.tierlist.TierLabel
import me.jfenn.bingo.common.card.tierlist.TierListConfig
import me.jfenn.bingo.common.card.tierlist.TierListEntry
import me.jfenn.bingo.common.config.ConfigService
import me.jfenn.bingo.common.data.ScopedData
import me.jfenn.bingo.common.data.TierListLoader
import me.jfenn.bingo.platform.IExecutors
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.model.ReloadEvent
import me.jfenn.bingo.platform.item.IItemStackFactory
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger
import java.util.concurrent.CompletableFuture

/**
 * Auto-classifies uncategorized, non-vanilla objectives into difficulty tiers.
 *
 * Hard rules (see [AutoTierConfig]): it only ever assigns tiers to objectives that are
 * uncategorized AND non-vanilla, and never touches vanilla content, mod-provided tier
 * lists, already-assigned entries, or unobtainable/unbreakable objectives.
 *
 * The result is written to `config/exbingo/tierlists/<autoTier.tierListName>.tierlist.json`
 * via [TierListLoader] (so [me.jfenn.bingo.common.config.TrackedFileService] keeps it in
 * sync with mod updates while never clobbering manual edits). It is the lowest-priority
 * list at card-assembly time, so mod categories always win.
 */
internal class AutoTierService(
    private val server: MinecraftServer,
    private val scopedData: ScopedData,
    private val objectiveManager: BingoObjectiveManager,
    private val itemStackFactory: IItemStackFactory,
    private val tagService: TagService,
    private val tierListLoader: TierListLoader,
    private val configService: ConfigService,
    private val eventBus: IEventBus,
    private val executors: IExecutors,
    private val log: Logger,
) {
    data class Result(
        val assigned: Int,
        val skipped: Int,
        val perTier: Map<TierLabel, Int>,
    )

    /** True if the objective belongs to base content (vanilla or ExBingo itself). */
    private fun isBaseNamespace(objectiveId: String): Boolean {
        val namespace = objectiveId.substringAfter('!')
            .substringBefore(':', missingDelimiterValue = MOD_ID_MINECRAFT)
        return namespace == MOD_ID_MINECRAFT || namespace == MOD_ID_BINGO
    }

    /** True if this objective is already categorized by any tier list OTHER than auto-tier. */
    private fun isCategorizedByOthers(objectiveId: String): Boolean {
        val autoTierName = configService.config.autoTier.tierListName
        return scopedData.tierLists
            .filterKeys { it != autoTierName }
            .values
            .any { it.isCategorized(objectiveId) }
    }

    /**
     * Computes the set of objectives that auto-tiering is allowed to assign, applying every
     * exclusion rule. Returns objective ids partitioned into items vs. advancements.
     */
    private fun collectTargets(): Pair<Set<String>, Set<String>> {
        val tags = tagService.getTags()
        val unobtainable = tags[ObjectiveFilter.UNOBTAINABLE]?.values ?: emptySet()
        val unbreakable = tags[ObjectiveFilter.UNBREAKABLE]?.values ?: emptySet()

        val items = mutableSetOf<String>()
        val advancements = mutableSetOf<String>()

        for (id in objectiveManager.list()) {
            // skip vanilla / ExBingo base content
            if (isBaseNamespace(id)) continue
            // skip anything a mod/datapack/manual list already categorizes
            if (isCategorizedByOthers(id)) continue
            // skip unobtainable & unbreakable
            if (unobtainable.contains(id) || unbreakable.contains(id)) continue
            // skip spawn eggs (typically creative-only, not legitimately obtainable)
            if (id.substringAfter(':').endsWith("_spawn_egg")) continue

            when (objectiveManager.find(id, CardGeneratorState.DEFAULT)) {
                is BingoObjective.ItemEntry -> items.add(id)
                is BingoObjective.AdvancementEntry -> advancements.add(id)
                else -> { /* other objective types are not auto-tiered */ }
            }
        }

        return items to advancements
    }

    /** Builds item obtain-sources from the server recipe manager, scoped to known items. */
    private fun buildItemSources(known: Set<String>): Map<String, AutoTierScorer.ItemSource> {
        val provider = server.registryAccess()
        val recipesByOutput = HashMap<String, MutableList<AutoTierScorer.Recipe>>()

        for (holder in server.recipeManager.recipes) {
            val recipe = holder.value()
            val output = try {
                recipe.getResultItem(provider)
            } catch (e: Throwable) {
                continue
            } ?: continue
            if (output.isEmpty) continue

            val outputId = output.item.let {
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(it).toString()
            }

            val ingredients = recipe.ingredients
                .asSequence()
                .filterNot { it.isEmpty }
                // take the first matching item of each ingredient as a representative
                .mapNotNull { ingredient ->
                    ingredient.items.firstOrNull()?.item?.let {
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(it).toString()
                    }
                }
                .toList()

            recipesByOutput.getOrPut(outputId) { mutableListOf() }
                .add(AutoTierScorer.Recipe(outputId, ingredients))
        }

        // every registered item: directly obtainable items get a base cost of 1.0;
        // items that only exist as recipe outputs derive their cost from depth.
        val allItems = itemStackFactory.listItems(server)
        val sources = HashMap<String, AutoTierScorer.ItemSource>()
        for (item in allItems) {
            val recipes = recipesByOutput[item].orEmpty()
            sources[item] = AutoTierScorer.ItemSource(
                recipes = recipes,
                // assume a direct source unless the item is purely a crafting output
                baseCost = if (recipes.isEmpty()) 1.0 else null,
            )
        }
        // include recipe outputs that aren't in listItems so their ingredients still resolve
        for ((output, recipes) in recipesByOutput) {
            sources.putIfAbsent(output, AutoTierScorer.ItemSource(recipes = recipes, baseCost = null))
        }
        return sources
    }

    /** Builds advancement scoring inputs from the server advancement tree. */
    private fun buildAdvancementInputs(targets: Set<String>): List<AutoTierScorer.AdvancementInput> {
        val tree = server.advancements.tree()
        return targets.mapNotNull { id ->
            val location = try {
                net.minecraft.resources.ResourceLocation.parse(id.substringAfter('!'))
            } catch (e: Throwable) {
                return@mapNotNull null
            }
            val node = tree.get(location) ?: return@mapNotNull AutoTierScorer.AdvancementInput(id, depth = 3, rootId = null)

            // depth = number of hops to the root
            var depth = 0
            var cursor = node.parent()
            while (cursor != null) {
                depth++
                cursor = cursor.parent()
            }
            val rootId = node.root().holder().id().toString()
            AutoTierScorer.AdvancementInput(id, depth, rootId)
        }
    }

    /**
     * Runs auto-tiering and persists the result.
     *
     * Already-assigned entries in the existing auto-tier file are preserved (so re-running
     * is incremental and stable); only brand-new uncategorized targets are added.
     */
    fun generate(): Result {
        val config = configService.config.autoTier
        val scorer = AutoTierScorer(config)
        val supportedObjectiveIds = objectiveManager.list().toSet()

        val (itemTargets, advancementTargets) = collectTargets()

        // score items (needs the full graph to resolve crafting depth, then filter to targets)
        val itemSources = buildItemSources(itemTargets)
        val allItemScores = scorer.scoreItems(itemSources)
        val itemScores = allItemScores.filterKeys { itemTargets.contains(it) }

        // score advancements
        val advancementScores = buildAdvancementInputs(advancementTargets)
            .associate { it.id to scorer.scoreAdvancement(it) }

        // Map items and advancements in SEPARATE quantile pools. A mod usually has far more
        // items than advancements; pooling them together lets the items dominate the buckets
        // and collapses the few advancements into a single tier (effectively unclassified).
        // Tiering each type on its own guarantees advancements get a proper S–D spread too.
        val newTiers = scorer.mapToTiers(itemScores) + scorer.mapToTiers(advancementScores)

        // preserve existing auto-tier assignments; only add newly-scored, still-uncategorized ids
        val existing = scopedData.tierLists[config.tierListName] ?: TierListConfig.EMPTY
        var result = existing
        val perTier = mutableMapOf<TierLabel, Int>()
        var assigned = 0

        for ((id, tier) in newTiers) {
            // never override an entry that already exists anywhere in the auto-tier file
            if (existing.contains(id)) continue
            result = result.plus(id, tier)
            perTier.merge(tier, 1, Int::plus)
            assigned++
        }

        // also drop any stale auto-tier entries that a mod/manual list now categorizes
        val pruned = result.filter { entry ->
            supportedObjectiveIds.contains(entry.item) && !isCategorizedByOthers(entry.item)
        }

        tierListLoader.writeTierList(config.tierListName, pruned.sort())

        val skipped = (itemTargets.size + advancementTargets.size) - assigned
        log.info("[AutoTierService] Assigned $assigned objectives across tiers $perTier (skipped $skipped)")
        return Result(assigned, skipped, perTier)
    }

    /** Runs [generate] then reloads bingo data so the new tier list takes effect immediately. */
    fun generateAndReload(): Result {
        val result = generate()
        val reloadEvent = ReloadEvent(server.resourceManager, executors.io, executors.main)
        eventBus.emit(ReloadEvent, reloadEvent)
            .let { CompletableFuture.allOf(*it.toTypedArray()) }
            .thenAcceptAsync({
                eventBus.emit(ReloadEvent.After, ReloadEvent.After())
            }, executors.main)
        return result
    }

    /** Clears the auto-tier list entirely (config/.../tierlists/<name>.tierlist.json). */
    fun clearAndReload() {
        tierListLoader.writeTierList(configService.config.autoTier.tierListName, TierListConfig.EMPTY)
        val reloadEvent = ReloadEvent(server.resourceManager, executors.io, executors.main)
        eventBus.emit(ReloadEvent, reloadEvent)
            .let { CompletableFuture.allOf(*it.toTypedArray()) }
            .thenAcceptAsync({
                eventBus.emit(ReloadEvent.After, ReloadEvent.After())
            }, executors.main)
    }
}
