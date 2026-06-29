package me.jfenn.bingo.common.card

import me.jfenn.bingo.common.MDC_DEBUG
import me.jfenn.bingo.common.MDC_FILENAME
import me.jfenn.bingo.common.MDC_OBJECTIVE
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.MOD_ID_MINECRAFT
import me.jfenn.bingo.common.card.data.ObjectiveRequirements
import me.jfenn.bingo.common.card.filter.ObjectiveFilter
import me.jfenn.bingo.common.card.filter.ObjectiveFilterList
import me.jfenn.bingo.common.card.filter.ObjectiveFilterService
import me.jfenn.bingo.common.card.objective.BingoObjective
import me.jfenn.bingo.common.card.objective.BingoObjectiveManager
import me.jfenn.bingo.common.card.objective.objectiveError
import me.jfenn.bingo.common.card.objective.withMdc
import me.jfenn.bingo.common.card.tag.TagData
import me.jfenn.bingo.common.card.tag.TagService
import me.jfenn.bingo.common.card.tierlist.TierLabel
import me.jfenn.bingo.common.card.tierlist.TierListConfig
import me.jfenn.bingo.common.card.tierlist.TierListEntry
import me.jfenn.bingo.common.config.ConfigService
import me.jfenn.bingo.common.data.ScopedData
import me.jfenn.bingo.common.game.GameCommands
import me.jfenn.bingo.common.options.BingoCardOptions
import me.jfenn.bingo.common.options.BingoOptions
import me.jfenn.bingo.common.spawn.SpawnKitService
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.common.utils.*
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.text.IText
import org.slf4j.Logger
import java.util.*
import kotlin.math.ceil
import kotlin.random.Random

internal class CardService(
    private val log: Logger,
    private val state: BingoState,
    private val options: BingoOptions,
    private val configService: ConfigService,
    private val data: ScopedData,
    private val tagService: TagService,
    private val objectiveManager: BingoObjectiveManager,
    private val tagExpansionService: TagExpansionService,
    private val text: TextProvider,
    private val spawnKitService: SpawnKitService,
    private val objectiveFilterService: ObjectiveFilterService,
) {

    private val supportedObjectiveIds = cacheFor(5.seconds) { _: Unit ->
        objectiveManager.list().toSet()
    }

    fun validateLists() {
        data.tags.entries
            .filter { (_, tag) -> Build.isDebug || tag.shouldValidate }
            .forEach { (name, tag) -> validateTag(name, tag) }
        data.tierLists.entries
            .filter { (_, list) -> Build.isDebug || list.shouldValidate }
            .forEach { (name, list) -> validateList(name, list) }
    }

    private fun validateTag(name: String, tag: TagData) = withMdc(
        MDC_FILENAME to "$name.json",
        MDC_DEBUG to "true",
    ) {
        log.info("[CardService] Validating tag $name.json...")
        val objectiveIds = tagExpansionService.expandItemTags(tag.values)
        for (objectiveId in objectiveIds) {
            val map = createObjectives(
                objectiveType = null,
                objectiveId = objectiveId,
                state = CardGeneratorState.DEFAULT
            )
            if (map == null) {
                log.objectiveError(objectiveId, "Not found")
            }
        }
    }

    private fun validateList(name: String, list: TierListConfig) = withMdc(
        MDC_FILENAME to "$name.tierlist.json",
        MDC_DEBUG to "true",
    ) {
        log.info("[CardService] Validating list $name.tierlist.json...")

        val listEntries = list.expandTags(tagExpansionService)
            .let {
                it.values
                    .plus(it.s + it.a + it.b + it.c + it.d)
                    .plus(it.groups.flatten().map { item -> TierListEntry(null, item) })
            }
        for (entry in listEntries) {
            val map = createObjectives(
                objectiveType = entry.type,
                objectiveId = entry.item,
                state = CardGeneratorState.DEFAULT
            )
            if (map == null) {
                log.objectiveError(entry.item, "Not found")
            }
        }
    }

    fun createInitialCards() {
        log.info("[CardService] Generating initial cards from game-options.json")

        // Clear any cards that already exist (avoids disconnected options ref)
        state.cards.clear()

        val cardOptionsList = options.cards
            .takeIf { it.isNotEmpty() && options.isValid() }
            ?: listOf(BingoCardOptions())

        options.cards = cardOptionsList

        // Note: cardOptionsList is reversed, since newCard adds to the front of the cards
        // (same as /bingo card push)
        for (cardOptions in cardOptionsList) {
            try {
                newCard(cardOptions).let { state.pushCardTail(it) }
            } catch (e: Throwable) {
                log.info("[CardService] Cannot create card from config:", e)
            }
        }

        // Update team card assignments (if any exist), because these cards will have new ids
        state.resetTeamCards()
    }

    fun isSupported(err: (IText) -> Unit): Boolean {
        // Check if every custom objective meets its requirements
        return state.cards
            .flatMap { it.objectives.values }
            .map { objective ->
                objective.data
                    ?.validate
                    ?.let {
                        val listName = objective.display.name ?: text.literal(objective.id)
                        checkSoftRequirements(listName, it, err)
                    }
                    ?: true
            }
            .all { it }
    }

    private fun checkSoftRequirements(
        listName: IText,
        reqs: ObjectiveRequirements,
        err: (IText) -> Unit,
    ): Boolean {
        var isSupported = true

        val teams = state.getRegisteredTeams()
        if (teams.size !in reqs.numTeams) {
            err(text.string(StringKey.CommandStartDataRecommendsNumTeams, listName, reqs.numTeams.formatString(), GameCommands.IGNORE_WARNINGS_COMMAND))
            isSupported = false
        }

        if (teams.sumOf { it.players.size } !in reqs.numPlayers) {
            err(text.string(StringKey.CommandStartDataRecommendsNumPlayers, listName, reqs.numPlayersPerTeam.formatString(), GameCommands.IGNORE_WARNINGS_COMMAND))
            isSupported = false
        }

        if (teams.any { it.players.size !in reqs.numPlayersPerTeam }) {
            err(text.string(StringKey.CommandStartDataRecommendsPlayersPerTeam, listName, reqs.numPlayersPerTeam.formatString(), GameCommands.IGNORE_WARNINGS_COMMAND))
            isSupported = false
        }

        return isSupported
    }

    fun newCard(cardOptions: BingoCardOptions): BingoCard {
        val newCard = generate(
            id = UUID.randomUUID(),
            seed = Random.nextLong(),
            cardOptions = cardOptions,
        )

        return newCard
    }

    fun shuffleCard(
        card: BingoCard,
        excludeObjectives: Set<String> = emptySet(),
    ) {
        if (!card.options.isValid()) throw IllegalArgumentException("Cannot reroll card; invalid options")

        // Change the BINGO seed to a new value
        val newSeed = Random.nextLong()
        val newCard = generate(
            id = card.id,
            seed = newSeed,
            cardOptions = card.options,
            excludeObjectives = excludeObjectives,
        )
        objectiveManager.init(newCard)
        state.replaceCard(newCard)
    }

    fun generateCard(
        card: BingoCard = state.getActiveCard(),
        options: BingoCardOptions = card.options,
        seed: Long = card.seed
    ): BingoCard {
        val newCard = generate(card.id, seed, options)
        state.replaceCard(newCard)
        return newCard
    }

    fun replaceEntry(card: BingoCard, x: Int, y: Int, objectiveId: String) {
        // Make sure that the objective exists
        val objective = objectiveManager.find(objectiveId, CardGeneratorState.DEFAULT)
            ?: throw IllegalArgumentException("Could not find objective '$objectiveId'")

        replaceEntry(card, x, y, objective)
    }

    fun replaceEntry(card: BingoCard, x: Int, y: Int, objective: BingoObjective) {
        // Overwrite the entry being replaced
        val entries = card.entries.toMutableList()
        entries[x + y * 5] = BingoCardEntry(
            objectiveId = objective.id,
            tier = null,
            source = null,
        )

        // Re-build the objectives map (but we don't need to check for conflicts)
        val objectives = mutableMapOf<String, BingoObjective>()
        for (entry in entries) {
            val objectiveMap = createObjectives(null, entry.objectiveId, CardGeneratorState.DEFAULT) ?: continue
            objectives.putAll(objectiveMap)
        }

        // If any objectives already exist on the card, use them instead of the new instance
        // (so that partial completion/state doesn't get lost if this is used mid-game)
        for (key in objectives.keys.toSet()) {
            card.objectives[key]?.let {
                objectives[key] = it
            }
        }

        objectives[objective.id] = objective

        assignTileNames(entries)
        // Set the card
        val newCard = BingoCard(
            id = card.id,
            seed = card.seed,
            entries = entries,
            objectives = objectives,
            options = card.options
        )
        state.replaceCard(newCard)
    }

    fun replaceEntries(
        card: BingoCard,
        seed: Long = card.seed + 1L,
        replaceEntries: List<BingoCardEntry>,
        excludeObjectives: Set<String> = emptySet(),
    ): BingoCard {
        val maxTier = TierLabel.entries
            .withIndex()
            .shuffled()
            .maxBy { (i, _) -> card.options.itemDistribution.getOrNull(i) ?: 0 }
            .value

        val dist = TierLabel.entries
            .map { tier -> replaceEntries.count { (it.tier ?: maxTier) == tier } }

        val replacements = generateObjectives(
            seed = seed,
            cardOptions = card.options,
            excludeObjectives = excludeObjectives + card.objectives.keys,
            dist = dist,
        )

        val entries = card.entries.toMutableList()
        for ((entry, replacement) in replaceEntries.zip(replacements)) {
            val index = entries.indexOf(entry)
            if (index == -1) continue
            entries[index] = replacement.entry
        }

        // Re-build the objectives map (but we don't need to check for conflicts)
        val objectives = mutableMapOf<String, BingoObjective>()
        for (entry in entries) {
            val objectiveMap = replacements.find { it.entry == entry }
                ?.objectives
                ?: createObjectives(null, entry.objectiveId, CardGeneratorState.DEFAULT)
                    ?.mapValues { (key, value) ->
                        // If any objectives already exist on the card, use them instead of the new instance
                        // (so that partial completion/state doesn't get lost)
                        card.objectives[key] ?: value
                    }
                ?: continue

            objectives.putAll(objectiveMap)
        }

        assignTileNames(entries)
        // Set the card
        val newCard = BingoCard(
            id = card.id,
            seed = seed,
            entries = entries,
            objectives = objectives,
            options = card.options
        )
        state.replaceCard(newCard)
        return newCard
    }

    private fun getFilterTag(tags: Map<String, TagData>, filter: ObjectiveFilter): TagData? {
        return tags[filter.tag] ?: run {
            if (!data.tierLists.containsKey(filter.tag) && filter.tag != ObjectiveFilter.UNCATEGORIZED) {
                log.error("[CardService] Objective tag '${filter.tag}' does not exist!")
            }
            null
        }
    }

    /**
     * Tags that ops have disabled via /bingo carddisable, expressed as exclusions to apply during
     * card generation. For every disabled board (filter preset) we take its `from=<mod>` and
     * `type=<type>` selectors and turn them into excludes, so the disabled mod/type content is
     * kept off cards even under broad filters like "Everything" that don't name the board. Other
     * selectors (e.g. tier-list names) are intentionally ignored, per the chosen rubric: only the
     * mod/type origin of a disabled board is excluded, never broader categories.
     */
    private fun disabledBoardExcludeTags(): Set<String> {
        val disabled = configService.config.disabledFilterPresets
        if (disabled.isEmpty()) return emptySet()

        val presets = objectiveFilterService.getAllPresetFilters()
        return buildSet {
            for (id in disabled) {
                val preset = presets[id] ?: continue
                for (filter in preset.value) {
                    if (filter is ObjectiveFilter.Include || filter is ObjectiveFilter.Count) {
                        if (filter.tag.startsWith("from=") || filter.tag.startsWith("type=")) {
                            add(filter.tag)
                        }
                    }
                }
            }
        }
    }

    private fun getTierLists(
        filterList: ObjectiveFilterList,
    ): Map<String, TierListConfig> {
        if (data.tierLists.isEmpty()) {
            throw IllegalStateException("[CardService] Cannot generate a card - tier lists have not been loaded yet!")
        }

        // If there are included tier lists, only include the referenced lists
        val hasIncludeList = filterList
            .filterIsInstance<ObjectiveFilter.Include>()
            .any { data.tierLists.containsKey(it.tag) }

        val lists = data.tierLists
            // Only include tier lists that aren't excluded
            .filterNot { (listName, _) ->
                filterList.filterIsInstance<ObjectiveFilter.Exclude>()
                    .any { it.tag == listName }
            }
            .filter { (listName, _) ->
                // If the list is included through either count or include filters, include it
                !hasIncludeList ||
                        filterList.filterIsInstance<ObjectiveFilter.Include>()
                            .any { it.tag == listName } ||
                        filterList.filterIsInstance<ObjectiveFilter.Count>()
                            .any { it.tag == listName }
            }

        return lists
    }

    private fun assembleTierList(
        tags: MutableMap<String, TagData>,
        filterList: ObjectiveFilterList,
    ): TierListConfig {
        // Collect the selected tier list config.
        //
        // Priority (highest first):
        //   1. manual edits      - guaranteed per-file by TrackedFileService
        //   2. mod/datapack lists - their categorization must win over auto-tier
        //   3. auto-tier list     - lowest; only fills gaps
        //
        // The auto-tier list is therefore combined LAST, and any entry it holds for an
        // objective that some other list already categorizes is dropped. This keeps
        // mod-provided categories authoritative even if a mod update categorizes an item
        // that a previous auto-tier run had already assigned (without needing a re-run).
        val autoTierName = configService.config.autoTier.tierListName
        val tierList = run {
            val selected = getTierLists(filterList)
                .map { (name, otherList) ->
                    name to otherList.expandTags(tagExpansionService).expandName(name)
                }

            // combine non-auto-tier (higher priority) lists first
            var list = TierListConfig.EMPTY
            selected
                .filter { (name, _) -> name != autoTierName }
                .forEach { (_, otherList) -> list = list.combine(otherList) }

            // then layer the auto-tier list underneath, minus anything already categorized
            selected
                .filter { (name, _) -> name == autoTierName }
                .forEach { (_, autoList) ->
                    val base = list
                    val pruned = autoList.filter { entry -> !base.isCategorized(entry.item) }
                    list = list.combine(pruned)
                }

            list
        }

        // Determine the set of uncategorized objectives & add them to the tags for filtering
        val uncategorizedObjectives = supportedObjectiveIds.get(Unit)
            .filterNot { tierList.contains(it) }
            .map { TierListEntry(null, it) }
            .onEach {
                it.listName = ObjectiveFilter.UNCATEGORIZED
            }

        tags[ObjectiveFilter.UNCATEGORIZED] = TagData(
            uncategorizedObjectives.map { it.item }.toSet()
        )

        // Determine the set of unobtainable items
        tags[ObjectiveFilter.UNOBTAINABLE] = TagData(
            (tags[ObjectiveFilter.UNOBTAINABLE]?.values ?: emptySet())
        )

        // Objectives that should not appear on cards by default, but can still be opted
        // into explicitly through an include/count filter (e.g. +unbreakable, +from=<mod>).
        //  - uncategorized content from non-vanilla namespaces (config-gated)
        //  - items whose placed block is unbreakable (config-gated)
        // Vanilla (minecraft:/exbingo:) uncategorized content keeps the original behavior.
        val defaultExcluded = buildSet {
            if (configService.config.excludeModUncategorizedFromCards) {
                uncategorizedObjectives
                    .filterNot { isBaseNamespace(it.item) }
                    .forEach { add(it.item) }
            }
            if (configService.config.excludeUnbreakableBlocksFromCards) {
                tags[ObjectiveFilter.UNBREAKABLE]?.values?.let { addAll(it) }
            }
        }

        // If there is any included tag, only include explicitly referenced items
        val hasIncludeTag = filterList.any { it is ObjectiveFilter.Include }

        val includeFilters = filterList
            // If a tag is included through either include or count filters, include it
            .filter { it is ObjectiveFilter.Include || it is ObjectiveFilter.Count }
            .mapNotNull { getFilterTag(tags, it) }
            .toMutableList()
        val excludeFilters = filterList
            .filterIsInstance<ObjectiveFilter.Exclude>()
            .mapNotNull { getFilterTag(tags, it) }
            .toMutableList()

        // Exclude the mod/type content of any board an op has disabled, so it never reaches a
        // card even under broad filters (e.g. "Everything") that don't name the board. Applied
        // on top of the explicit exclude filters above.
        disabledBoardExcludeTags()
            .mapNotNull { tags[it] }
            .forEach { excludeFilters.add(it) }

        // Default-excluded content (mod-uncategorized & unbreakable) may ONLY be brought
        // back by a *targeted* opt-in — i.e. the filter explicitly names the `uncategorized`
        // or `unbreakable` tag. Broad includes like `from=<mod>` or `type=advancement` must
        // NOT pull in uncategorized content, so mod boards and the all-advancements board
        // stay free of unbalanced uncategorized items/advancements.
        val optInTags = setOf(ObjectiveFilter.UNCATEGORIZED, ObjectiveFilter.UNBREAKABLE)
        val optInFilters = filterList
            .filter { (it is ObjectiveFilter.Include || it is ObjectiveFilter.Count) && it.tag in optInTags }
            .mapNotNull { getFilterTag(tags, it) }
            .toMutableList()

        return tierList
            .copy(
                // include any uncategorized objectives in the list
                values = tierList.values + uncategorizedObjectives.toSet()
            )
            .filter { entry ->
                // apply the selected item filters to the list contents
                val isIncluded = !hasIncludeTag || includeFilters.any { it.contains(entry.item) }
                val isExcluded = excludeFilters.any { it.contains(entry.item) }
                // drop default-excluded objectives unless a targeted opt-in filter brought them in
                val isOptedIn = optInFilters.any { it.contains(entry.item) }
                val isDefaultExcluded = defaultExcluded.contains(entry.item) && !isOptedIn
                isIncluded && !isExcluded && !isDefaultExcluded
            }
    }

    /** Base-game content (vanilla + ExBingo's own objectives) keeps the original card behavior. */
    private fun isBaseNamespace(objectiveId: String): Boolean {
        val namespace = objectiveId.substringAfter('!').substringBefore(':', missingDelimiterValue = MOD_ID_MINECRAFT)
        return namespace == MOD_ID_MINECRAFT || namespace == MOD_ID_BINGO
    }

    data class GeneratedObjective(
        val entry: BingoCardEntry,
        val objectives: Map<String, BingoObjective>,
    ) {
        companion object {
            fun freeSpace() = run {
                val freeSpace = BingoObjective.FreeSpace()
                GeneratedObjective(
                    entry = BingoCardEntry(
                        objectiveId = freeSpace.id,
                        tier = null,
                        source = null,
                    ),
                    objectives = mapOf(
                        freeSpace.id to freeSpace,
                    )
                )
            }
        }
    }

    private fun generateObjectives(
        seed: Long,
        cardOptions: BingoCardOptions,
        excludeObjectives: Set<String>,
        dist: List<Int>,
        minItems: Int = dist.sum(),
    ): List<GeneratedObjective> {
        val items = mutableListOf<GeneratedObjective>()

        // Exclude any items from the card that the player might spawn with
        // (e.g. elytra mode, player/team kits...)
        val excludedObjectives = if (configService.config.excludeSpawnKitItemsFromCards) {
            buildSet {
                if (options.isPlayerKit)
                    addAll(spawnKitService.getPlayerItems().map { it.identifier.toString() })
                if (options.isTeamKit)
                    addAll(spawnKitService.getTeamItems().map { it.identifier.toString() })
                if (options.isElytra) {
                    add("minecraft:elytra")
                    add("minecraft:firework_rocket")
                }
            }.toMutableSet()
        } else mutableSetOf()
        excludedObjectives.addAll(excludeObjectives)

        val random = Random(seed)
        val state = CardGeneratorState(random)
        log.info("[CardService] Using random BINGO card seed: $seed")

        val tags = tagService.getTags().toMutableMap()
        val filterList = cardOptions.itemFilter
            .filter {
                if (tags.containsKey(it.tag)) true else {
                    log.error("[CardService] Tag $it in filters does not exist!")
                    false
                }
            }
            .let { ObjectiveFilterList(it) }
        val tierList = assembleTierList(tags, filterList)

        // Track the tags added to the card to enforce filter requirements
        val requiredTagCounts = filterList
            .filterIsInstance<ObjectiveFilter.Count>()
            .mapNotNull { filter ->
                val tag = getFilterTag(tags, filter) ?: return@mapNotNull null
                var remainingCount = filter.count
                // For each tag filter, distribute its items between the item dist
                // (this may not exactly add up to the remainingCount)
                val filterDist = dist.map { distItems ->
                    val count = ceil(filter.count.toDouble() * distItems / 25)
                        .toInt()
                        .coerceAtMost(remainingCount)

                    remainingCount -= count
                    count
                }

                tag to filterDist
            }

        fun createObjectivePair(entry: TierListEntry): GeneratedObjective? {
            val objectiveMap = createObjectives(entry.type, entry.item, state)
                ?: return null

            val objectiveConflicts = objectiveMap.values
                .flatMap { it.conflictsWithObjectives }
                .filter { it != entry.item } // any direct objectiveId conflicts are handled by list.pick()
            if (objectiveConflicts.any { excludedObjectives.contains(it) })
                return null

            return GeneratedObjective(
                entry = BingoCardEntry(
                    objectiveId = entry.item,
                    tier = entry.tierLabel,
                    source = entry.listName,
                ),
                objectives = objectiveMap,
            )
        }

        fun addToExcluded(objective: GeneratedObjective) {
            // if the chosen item is in any item groups, exclude all of those groups
            excludedObjectives.add(objective.entry.objectiveId)
            for (group in tierList.groups.filter { group -> group.contains(objective.entry.objectiveId) }) {
                excludedObjectives.addAll(group)
            }

            // add any dependencies to the excluded list to prevent conflicting goals
            // - this prevents "craft a shield" and "never obtain shield" from appearing on the same card
            excludedObjectives.addAll(objective.objectives.values.flatMap { it.conflictsWithObjectives })
        }

        // Pick the given number of items from the tier list for each tier
        for ((tierIndex, tier) in TierLabel.entries.withIndex()) {
            var tierCountRemaining = dist[tierIndex]

            // First, attempt to add items that satisfy the required filter counts
            for ((tag, count) in requiredTagCounts.shuffled(random)) {
                val itemCount = count[tierIndex].coerceAtMost(tierCountRemaining)

                tierList.pick(tier, excludedObjectives, random)
                    .filter { tag.contains(it.item) }
                    .mapNotNull { createObjectivePair(it) }
                    .take(itemCount)
                    .onEach { addToExcluded(it) }
                    .toList()
                    .let {
                        items.addAll(it)
                        tierCountRemaining -= it.size
                    }
            }

            // Then, add remaining items that satisfy other filter conditions
            tierList.pick(tier, excludedObjectives, random)
                // exclude required count tags when picking normal items (otherwise the actual counts may differ)
                .filter { entry ->
                    requiredTagCounts.none { (tag, _) -> tag.contains(entry.item) }
                }
                .mapNotNull { createObjectivePair(it) }
                .take(tierCountRemaining)
                .onEach { addToExcluded(it) }
                .toList()
                .let {
                    items.addAll(it)
                    tierCountRemaining -= it.size
                }
        }

        if (items.size < dist.sum()) {
            log.error("Card has generated with ${items.size} entries, when the item distribution needed ${dist.sum()}!")
            log.error("This might be caused by a small tier list or too many item groups in the configuration...")
        }

        // if the card has less than 25 entries, add free spaces until it is full
        while (items.size < minItems) {
            items.add(GeneratedObjective.freeSpace())
        }

        items.shuffle(random)
        return items.take(minItems)
    }

    fun generate(
        id: UUID,
        seed: Long,
        cardOptions: BingoCardOptions,
        excludeObjectives: Set<String> = emptySet(),
    ): BingoCard {
        cardOptions.assertValid()

        val generatedObjectives = generateObjectives(
            seed = seed,
            cardOptions = cardOptions,
            excludeObjectives = excludeObjectives,
            dist = cardOptions.itemDistribution,
            minItems = 25,
        )

        return BingoCard(
            id = id,
            seed = seed,
            entries = generatedObjectives
                .map { it.entry }
                .also { assignTileNames(it) },
            objectives = generatedObjectives
                .flatMap { it.objectives.entries }
                .toMap(),
            options = cardOptions
        )
    }

    private fun assignTileNames(entries: List<BingoCardEntry>) {
        val cols = "bingo".toCharArray()
        val rows = "12345".toCharArray()
        entries.forEachIndexed { i, entry ->
            val col = cols.getOrNull(i % 5)
            val row = rows.getOrNull(i / 5)
            if (col != null && row != null)
                entry.tileName = "$col$row"
        }
    }

    private fun createObjectives(
        objectiveType: String?,
        objectiveId: String,
        state: CardGeneratorState,
        objectives: MutableMap<String, BingoObjective> = mutableMapOf(),
    ): Map<String, BingoObjective>? {
        val id = objectiveType?.let { "$it!$objectiveId" } ?: objectiveId
        return withMdc(MDC_OBJECTIVE to id) {
            val objective = objectiveManager.find(id, state) ?: return null

            objectives[objectiveId] = objective

            val resolvedObjectives = objective.dependsOnObjectives
                .mapNotNull {
                    objectives[it] ?: createObjectives(null, it, state, objectives)?.get(it)
                }
                .map { it.id }
                .toSet()

            // if any dependencies fail to resolve, return null
            if (!objective.satisfiesDependencies(resolvedObjectives)) {
                log.objectiveError(objectiveId, "Could not resolve dependencies")
                return null
            }

            if (resolvedObjectives.size < objective.dependsOnObjectives.size) {
                log.objectiveError(objectiveId, "Only ${resolvedObjectives.size} of ${objective.dependsOnObjectives.size} dependencies could be resolved")
            }

            objectives
        }
    }
}