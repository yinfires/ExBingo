package me.jfenn.bingo.common.config

import kotlinx.serialization.encodeToString
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.card.autotier.AutoTierConfig
import me.jfenn.bingo.common.card.filter.ObjectiveFilterList
import me.jfenn.bingo.common.card.tierlist.TierLabel
import me.jfenn.bingo.common.utils.decodeFromUtf8Stream
import me.jfenn.bingo.common.utils.json
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.config.ModConfig
import net.neoforged.fml.event.config.ModConfigEvent
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.ModConfigSpec
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.function.Predicate
import java.util.function.Supplier
import kotlin.io.path.exists

object NeoForgeConfigBridge {
    const val COMMON_FILE_NAME = "$MOD_ID_BINGO-common.toml"
    const val CLIENT_FILE_NAME = "$MOD_ID_BINGO-client.toml"
    const val DISABLED_SECONDS = -1

    private val log = LoggerFactory.getLogger("ExBingo")
    private val legacyConfigRelativePath = Path.of(MOD_ID_BINGO, "config.json")

    private val commonPair = ModConfigSpec.Builder().configure(::CommonValues)
    private val clientPair = ModConfigSpec.Builder().configure(::ClientValues)

    val commonValues: CommonValues = commonPair.left
    val commonSpec: ModConfigSpec = commonPair.right
    val clientValues: ClientValues = clientPair.left
    val clientSpec: ModConfigSpec = clientPair.right

    val mirroredConfigPaths: Set<String> =
        (commonValues.entries + clientValues.entries).map { it.configPath }.toSet()

    val intentionallyJsonOnlyConfigPaths: Map<String, String> = mapOf(
        "version" to "Internal migration marker; it is managed by MigrationHandler.",
    )

    val requiredTranslationKeys: Set<String> =
        buildSet {
            add("$MOD_ID_BINGO.configuration.title")
            add("$MOD_ID_BINGO.configuration.section.exbingo.common.toml")
            add("$MOD_ID_BINGO.configuration.section.exbingo.common.toml.title")
            add("$MOD_ID_BINGO.configuration.section.exbingo.client.toml")
            add("$MOD_ID_BINGO.configuration.section.exbingo.client.toml.title")

            (commonValues.sections + clientValues.sections).forEach {
                add(it.translationKey)
                add("${it.translationKey}.tooltip")
            }

            (commonValues.entries + clientValues.entries).forEach {
                add(it.translationKey)
                add("${it.translationKey}.tooltip")
            }

            add("$MOD_ID_BINGO.configuration.common.auto_tier.mapping.quantile")
            add("$MOD_ID_BINGO.configuration.common.auto_tier.mapping.threshold")
        }

    fun registerConfigs(modContainer: ModContainer, modEventBus: IEventBus) {
        modContainer.registerConfig(ModConfig.Type.COMMON, commonSpec, COMMON_FILE_NAME)
        modContainer.registerConfig(ModConfig.Type.CLIENT, clientSpec, CLIENT_FILE_NAME)
        modEventBus.addListener(ModConfigEvent.Loading::class.java, this::onConfigEvent)
        modEventBus.addListener(ModConfigEvent.Reloading::class.java, this::onConfigEvent)
    }

    fun onConfigEvent(event: ModConfigEvent) {
        val config = event.config
        if (config.modId != MOD_ID_BINGO || config.fileName !in setOf(COMMON_FILE_NAME, CLIENT_FILE_NAME)) {
            return
        }

        runCatching {
            syncLoadedSpecsToJson(FMLPaths.CONFIGDIR.get())
        }.onFailure {
            log.error("Unable to sync ExBingo NeoForge config values into config/exbingo/config.json", it)
        }
    }

    fun applyLoadedSpecs(config: BingoConfig): BingoConfig {
        var result = config
        if (commonSpec.isLoaded) {
            result = commonValues.applyTo(result)
        }
        if (clientSpec.isLoaded) {
            result = result.copy(client = clientValues.applyTo(result.client))
        }
        return result
    }

    fun updateLoadedSpecsFrom(config: BingoConfig) {
        var saveCommon = false
        var saveClient = false

        if (commonSpec.isLoaded) {
            commonValues.setFrom(config)
            saveCommon = true
        }

        if (clientSpec.isLoaded) {
            clientValues.setFrom(config.client)
            saveClient = true
        }

        if (saveCommon) {
            commonSpec.save()
        }
        if (saveClient) {
            clientSpec.save()
        }
    }

    private fun syncLoadedSpecsToJson(configDir: Path) {
        val current = readLegacyConfig(configDir)
        val updated = applyLoadedSpecs(current)
        writeLegacyConfig(configDir, updated)
    }

    private fun defaultConfig(): BingoConfig =
        runCatching { readLegacyConfig(FMLPaths.CONFIGDIR.get()) }
            .getOrElse { BingoConfig() }

    private fun readLegacyConfig(configDir: Path): BingoConfig {
        val path = configDir.resolve(legacyConfigRelativePath)
        if (!path.exists()) {
            return BingoConfig()
        }

        return runCatching {
            Files.newInputStream(path).use { json.decodeFromUtf8Stream<BingoConfig>(it) }
        }.onFailure {
            log.error("Unable to read ExBingo config from {}", path, it)
        }.getOrElse { BingoConfig() }
    }

    private fun writeLegacyConfig(configDir: Path, config: BingoConfig) {
        val path = configDir.resolve(legacyConfigRelativePath)
        Files.createDirectories(path.parent)
        Files.newBufferedWriter(path).use {
            it.write(json.encodeToString(config))
        }
    }

    class CommonValues(builder: ModConfigSpec.Builder) {
        val entries = mutableListOf<ConfigEntry>()
        val sections = mutableListOf<ConfigSection>()

        init {
            builder.pushSection("cards", "Board and card-generation defaults.", commonSectionKey("cards"), sections)
        }

        val disabledFilterPresets = builder.defineStringList(
            "disabledFilterPresets",
            commonKey("cards", "disabled_filter_presets"),
            "Board preset ids hidden from board selection.",
            "disabledFilterPresets",
            { defaultConfig().disabledFilterPresets.toList().sorted() },
            entries,
            allowBlank = false,
        )
        val itemFilterPresets = builder.defineStringList(
            "itemFilterPresets",
            commonKey("cards", "item_filter_presets"),
            "Board preset definitions written as preset_id=filter expression.",
            "itemFilterPresets",
            { itemFilterPresetList(defaultConfig().itemFilterPresets) },
            entries,
            allowBlank = false,
            validator = ::isItemFilterPresetEntry,
        )
        val difficultyPresets = builder.defineStringList(
            "difficultyPresets",
            commonKey("cards", "difficulty_presets"),
            "Card difficulty distributions written as preset_id=s,a,b,c,d. The five counts must add up to 25.",
            "difficultyPresets",
            { difficultyPresetList(defaultConfig().difficultyPresets) },
            entries,
            allowBlank = false,
            validator = ::isDifficultyPresetEntry,
        )
        val preventScoringSpawnKitItems = builder.defineBoolean(
            "preventScoringSpawnKitItems",
            commonKey("cards", "prevent_scoring_spawn_kit_items"),
            "Adds bingo_ignore to spawn kit items so they cannot score card tiles.",
            "preventScoringSpawnKitItems",
            { defaultConfig().preventScoringSpawnKitItems },
            entries,
        )
        val excludeSpawnKitItemsFromCards = builder.defineBoolean(
            "excludeSpawnKitItemsFromCards",
            commonKey("cards", "exclude_spawn_kit_items_from_cards"),
            "Removes active spawn kit items from generated cards.",
            "excludeSpawnKitItemsFromCards",
            { defaultConfig().excludeSpawnKitItemsFromCards },
            entries,
        )
        val excludeModUncategorizedFromCards = builder.defineBoolean(
            "excludeModUncategorizedFromCards",
            commonKey("cards", "exclude_mod_uncategorized_from_cards"),
            "Keeps uncategorized non-vanilla objectives off cards unless a filter selects them explicitly.",
            "excludeModUncategorizedFromCards",
            { defaultConfig().excludeModUncategorizedFromCards },
            entries,
        )
        val excludeUnbreakableBlocksFromCards = builder.defineBoolean(
            "excludeUnbreakableBlocksFromCards",
            commonKey("cards", "exclude_unbreakable_blocks_from_cards"),
            "Keeps objectives for unbreakable placed blocks off cards.",
            "excludeUnbreakableBlocksFromCards",
            { defaultConfig().excludeUnbreakableBlocksFromCards },
            entries,
        )

        init {
            builder.pop()
            builder.pushSection("autoTier", "Automatic tiering defaults for uncategorized modded objectives.", commonSectionKey("auto_tier"), sections)
        }

        val autoTierName = builder.defineString(
            "autoTier.tierListName",
            commonKey("auto_tier", "tier_list_name"),
            "Tier list file name written by /bingo autotier generate.",
            "tierListName",
            { defaultConfig().autoTier.tierListName },
            entries,
            allowBlank = false,
        )
        val autoTierMapping = builder.defineEnum(
            "autoTier.mapping",
            commonKey("auto_tier", "mapping"),
            "Controls how auto-tier scores are converted to S/A/B/C/D tiers.",
            "mapping",
            { defaultConfig().autoTier.mapping },
            entries,
        )
        val autoTierThresholds = builder.defineDoubleList(
            "autoTier.thresholds",
            commonKey("auto_tier", "thresholds"),
            "Absolute score cutoffs for S, A, B, C, and D when mapping is THRESHOLD.",
            "thresholds",
            { defaultConfig().autoTier.thresholds },
            entries,
            min = 0.0,
            max = 100000.0,
        )
        val autoTierRecipeStepCost = builder.defineDouble(
            "autoTier.recipeStepCost",
            commonKey("auto_tier", "recipe_step_cost"),
            "Score added for each recipe step while auto-tiering.",
            "recipeStepCost",
            { defaultConfig().autoTier.recipeStepCost },
            entries,
            min = 0.0,
            max = 100000.0,
        )

        init {
            builder.pop()
            builder.pushSection("lobby", "Lobby, tutorial, and postgame behavior.", commonSectionKey("lobby"), sections)
        }

        val preventLobbyChaos = builder.defineBoolean(
            "preventLobbyChaos",
            commonKey("lobby", "prevent_lobby_chaos"),
            "Stops lobby sounds from being sent to other players.",
            "preventLobbyChaos",
            { defaultConfig().preventLobbyChaos },
            entries,
        )
        val lobbyTutorialBook = builder.defineBoolean(
            "lobbyTutorialBook",
            commonKey("lobby", "lobby_tutorial_book"),
            "Gives players a tutorial/settings book when they join the lobby.",
            "lobbyTutorialBook",
            { defaultConfig().lobbyTutorialBook },
            entries,
        )
        val giveMementoInSurvival = builder.defineBoolean(
            "giveMementoInSurvival",
            commonKey("lobby", "give_memento_in_survival"),
            "Gives players a memento card after a survival-mode game ends.",
            "giveMementoInSurvival",
            { defaultConfig().giveMementoInSurvival },
            entries,
        )
        val nightVisionInSpectator = builder.defineBoolean(
            "nightVisionInSpectator",
            commonKey("lobby", "night_vision_in_spectator"),
            "Grants night vision while spectating when player settings allow it.",
            "nightVisionInSpectator",
            { defaultConfig().nightVisionInSpectator },
            entries,
        )
        val nightVisionInPostgame = builder.defineBoolean(
            "nightVisionInPostgame",
            commonKey("lobby", "night_vision_in_postgame"),
            "Grants night vision after a game ends when player settings allow it.",
            "nightVisionInPostgame",
            { defaultConfig().nightVisionInPostgame },
            entries,
        )
        val revealAllAdvancements = builder.defineBoolean(
            "revealAllAdvancements",
            commonKey("lobby", "reveal_all_advancements"),
            "Shows the whole vanilla advancement tree from the start.",
            "revealAllAdvancements",
            { defaultConfig().revealAllAdvancements },
            entries,
        )
        val supportClientHud = builder.defineBoolean(
            "supportClientHud",
            commonKey("lobby", "support_client_hud"),
            "Allows connecting clients to use the Bingo HUD instead of only map/card behavior.",
            "supportClientHud",
            { defaultConfig().supportClientHud },
            entries,
        )

        init {
            builder.pop()
            builder.pushSection("flow", "Ready timers, countdowns, and reset behavior.", commonSectionKey("flow"), sections)
        }

        val startWhenReadySeconds = builder.defineInt(
            "startWhenReadySeconds",
            commonKey("flow", "start_when_ready_seconds"),
            "Seconds before the game starts automatically; -1 disables this timer.",
            "startWhenReadySeconds",
            { defaultConfig().startWhenReadySeconds ?: DISABLED_SECONDS },
            entries,
            min = DISABLED_SECONDS,
            max = 86400,
        )
        val startWhenReadyWaitsForTeams = builder.defineBoolean(
            "startWhenReadyWaitsForTeams",
            commonKey("flow", "start_when_ready_waits_for_teams"),
            "Prevents the ready timer from starting until teams are available.",
            "startWhenReadyWaitsForTeams",
            { defaultConfig().startWhenReadyWaitsForTeams },
            entries,
        )
        val startWhenReadyWaitsForFirstVote = builder.defineBoolean(
            "startWhenReadyWaitsForFirstVote",
            commonKey("flow", "start_when_ready_waits_for_first_vote"),
            "Prevents the ready timer from starting until someone votes ready.",
            "startWhenReadyWaitsForFirstVote",
            { defaultConfig().startWhenReadyWaitsForFirstVote },
            entries,
        )
        val nextRoundWhenReadySeconds = builder.defineInt(
            "nextRoundWhenReadySeconds",
            commonKey("flow", "next_round_when_ready_seconds"),
            "Seconds before the next round starts automatically; -1 disables this timer.",
            "nextRoundWhenReadySeconds",
            { defaultConfig().nextRoundWhenReadySeconds ?: DISABLED_SECONDS },
            entries,
            min = DISABLED_SECONDS,
            max = 86400,
        )
        val nextRoundWhenReadyWaitsForFirstVote = builder.defineBoolean(
            "nextRoundWhenReadyWaitsForFirstVote",
            commonKey("flow", "next_round_when_ready_waits_for_first_vote"),
            "Prevents the next-round ready timer from starting until someone votes ready.",
            "nextRoundWhenReadyWaitsForFirstVote",
            { defaultConfig().nextRoundWhenReadyWaitsForFirstVote },
            entries,
        )
        val countdownDelayTicks = builder.defineInt(
            "countdownDelayTicks",
            commonKey("flow", "countdown_delay_ticks"),
            "Ticks to wait before the start countdown begins.",
            "countdownDelayTicks",
            { defaultConfig().countdownDelayTicks },
            entries,
            min = 0,
            max = 72000,
        )
        val countdownSeconds = builder.defineInt(
            "countdownSeconds",
            commonKey("flow", "countdown_seconds"),
            "Visible countdown length in seconds.",
            "countdownSeconds",
            { defaultConfig().countdownSeconds },
            entries,
            min = 0,
            max = 3600,
        )
        val nextRoundWhenEveryoneDisconnects = builder.defineBoolean(
            "nextRoundWhenEveryoneDisconnects",
            commonKey("flow", "next_round_when_everyone_disconnects"),
            "Starts the next round or reset flow after all players disconnect in postgame.",
            "nextRoundWhenEveryoneDisconnects",
            { defaultConfig().nextRoundWhenEveryoneDisconnects },
            entries,
        )
        val unsafeSkipWorldClose = builder.defineBoolean(
            "unsafeSkipWorldClose",
            commonKey("flow", "unsafe_skip_world_close"),
            "Skips part of world close during reset. This is faster but can risk bugs or data corruption.",
            "unsafeSkipWorldClose",
            { defaultConfig().unsafeSkipWorldClose },
            entries,
        )

        init {
            builder.pop()
            builder.pushSection("chat", "Team chat and command alias defaults.", commonSectionKey("chat"), sections)
        }

        val chatDefaultToTeamChat = builder.defineBoolean(
            "chat.defaultToTeamChat",
            commonKey("chat", "default_to_team_chat"),
            "New players use team chat by default when they are on a team.",
            "defaultToTeamChat",
            { defaultConfig().chat.defaultToTeamChat },
            entries,
        )
        val chatDefaultToSpectatorChat = builder.defineBoolean(
            "chat.defaultToSpectatorChat",
            commonKey("chat", "default_to_spectator_chat"),
            "Spectators use spectator chat by default.",
            "defaultToSpectatorChat",
            { defaultConfig().chat.defaultToSpectatorChat },
            entries,
        )
        val chatGlobalCommandAliases = builder.defineStringList(
            "chat.globalCommandAliases",
            commonKey("chat", "global_command_aliases"),
            "Command aliases that send messages to global chat.",
            "globalCommandAliases",
            { defaultConfig().chat.globalCommandAliases },
            entries,
            allowBlank = false,
        )
        val chatTeamCommandAliases = builder.defineStringList(
            "chat.teamCommandAliases",
            commonKey("chat", "team_command_aliases"),
            "Command aliases that send messages to team chat.",
            "teamCommandAliases",
            { defaultConfig().chat.teamCommandAliases },
            entries,
            allowBlank = false,
        )

        init {
            builder.pop()
            builder.pushSection("stats", "Stats storage and sync settings.", commonSectionKey("stats"), sections)
        }

        val databaseUrl = builder.defineString(
            "databaseUrl",
            commonKey("stats", "database_url"),
            "Optional JDBC database URL. Leave blank to use the bundled SQLite database.",
            "databaseUrl",
            { defaultConfig().databaseUrl.orEmpty() },
            entries,
            allowBlank = true,
        )
        val databaseUser = builder.defineString(
            "databaseUser",
            commonKey("stats", "database_user"),
            "Optional database username.",
            "databaseUser",
            { defaultConfig().databaseUser.orEmpty() },
            entries,
            allowBlank = true,
        )
        val databasePass = builder.defineString(
            "databasePass",
            commonKey("stats", "database_pass"),
            "Optional database password. This is stored as plain text in the config file.",
            "databasePass",
            { defaultConfig().databasePass.orEmpty() },
            entries,
            allowBlank = true,
        )
        val syncStats = builder.defineBoolean(
            "syncStats",
            commonKey("stats", "sync_stats"),
            "Syncs Bingo statistics with compatible servers and clients.",
            "syncStats",
            { defaultConfig().syncStats },
            entries,
        )
        val statsHostId = builder.defineString(
            "statsHostId",
            commonKey("stats", "stats_host_id"),
            "Unique host id used to distinguish locally played games from synced server games.",
            "statsHostId",
            { defaultConfig().statsHostId.toString() },
            entries,
            allowBlank = false,
            validator = { runCatching { UUID.fromString(it) }.isSuccess },
        )

        init {
            builder.pop()
            builder.pushSection("server", "Dedicated-server and reset-file behavior.", commonSectionKey("server"), sections)
        }

        val serverIsLobbyMode = builder.defineBoolean(
            "server.isLobbyMode",
            commonKey("server", "is_lobby_mode"),
            "Enables lobby mode. In lobby mode ExBingo can delete and recreate world data after games.",
            "isLobbyMode",
            { defaultConfig().server.isLobbyMode },
            entries,
        )
        val serverPreloadViewDistance = builder.defineInt(
            "server.preloadViewDistance",
            commonKey("server", "preload_view_distance"),
            "Chunk view distance used while preloading team spawn areas.",
            "preloadViewDistance",
            { defaultConfig().server.preloadViewDistance },
            entries,
            min = 0,
            max = 32,
        )
        val serverFilesToReset = builder.defineStringList(
            "server.filesToReset",
            commonKey("server", "files_to_reset"),
            "World-save paths or globs removed when ExBingo resets the world.",
            "filesToReset",
            { defaultConfig().server.filesToReset },
            entries,
            allowBlank = false,
        )

        init {
            builder.pop()
            builder.pushSection("defaultPlayerSettings", "Defaults for newly created per-player settings.", commonSectionKey("default_player_settings"), sections)
        }

        val defaultSeenTutorial = builder.defineBoolean(
            "server.defaultPlayerSettings.seenTutorial",
            commonKey("default_player_settings", "seen_tutorial"),
            "Default value for whether a player has seen the intro tutorial.",
            "seenTutorial",
            { defaultConfig().server.defaultPlayerSettings.seenTutorial },
            entries,
        )
        val defaultHideLobbyPrompt = builder.defineBoolean(
            "server.defaultPlayerSettings.hideLobbyPrompt",
            commonKey("default_player_settings", "hide_lobby_prompt"),
            "Default value for hiding the lobby prompt.",
            "hideLobbyPrompt",
            { defaultConfig().server.defaultPlayerSettings.hideLobbyPrompt },
            entries,
        )
        val defaultBossbar = builder.defineBoolean(
            "server.defaultPlayerSettings.bossbar",
            commonKey("default_player_settings", "bossbar"),
            "Default value for showing game information in a bossbar.",
            "bossbar",
            { defaultConfig().server.defaultPlayerSettings.bossbar },
            entries,
        )
        val defaultScoreboard = builder.defineBoolean(
            "server.defaultPlayerSettings.scoreboard",
            commonKey("default_player_settings", "scoreboard"),
            "Default value for showing game information in a scoreboard.",
            "scoreboard",
            { defaultConfig().server.defaultPlayerSettings.scoreboard },
            entries,
        )
        val defaultScoreboardAutoHide = builder.defineBoolean(
            "server.defaultPlayerSettings.scoreboardAutoHide",
            commonKey("default_player_settings", "scoreboard_auto_hide"),
            "Default value for hiding the scoreboard when the player is not holding a Bingo card.",
            "scoreboardAutoHide",
            { defaultConfig().server.defaultPlayerSettings.scoreboardAutoHide },
            entries,
        )
        val defaultLeadingMessages = builder.defineBoolean(
            "server.defaultPlayerSettings.leadingMessages",
            commonKey("default_player_settings", "leading_messages"),
            "Default value for floating messages when the leading team changes.",
            "leadingMessages",
            { defaultConfig().server.defaultPlayerSettings.leadingMessages },
            entries,
        )
        val defaultScoreMessages = builder.defineBoolean(
            "server.defaultPlayerSettings.scoreMessages",
            commonKey("default_player_settings", "score_messages"),
            "Default value for floating messages when lines or cards are scored.",
            "scoreMessages",
            { defaultConfig().server.defaultPlayerSettings.scoreMessages },
            entries,
        )
        val defaultItemMessages = builder.defineBoolean(
            "server.defaultPlayerSettings.itemMessages",
            commonKey("default_player_settings", "item_messages"),
            "Default value for floating messages when items are scored.",
            "itemMessages",
            { defaultConfig().server.defaultPlayerSettings.itemMessages },
            entries,
        )
        val defaultNightVision = builder.defineBoolean(
            "server.defaultPlayerSettings.nightVision",
            commonKey("default_player_settings", "night_vision"),
            "Default value for opt-in night vision.",
            "nightVision",
            { defaultConfig().server.defaultPlayerSettings.nightVision },
            entries,
        )

        init {
            builder.pop()
        }

        fun applyTo(config: BingoConfig): BingoConfig =
            config.copy(
                itemFilterPresets = parseItemFilterPresetList(itemFilterPresets.get()),
                difficultyPresets = parseDifficultyPresetList(difficultyPresets.get()),
                disabledFilterPresets = disabledFilterPresets.get().toSet(),
                preventScoringSpawnKitItems = preventScoringSpawnKitItems.get(),
                excludeSpawnKitItemsFromCards = excludeSpawnKitItemsFromCards.get(),
                excludeModUncategorizedFromCards = excludeModUncategorizedFromCards.get(),
                excludeUnbreakableBlocksFromCards = excludeUnbreakableBlocksFromCards.get(),
                autoTier = AutoTierConfig(
                    tierListName = autoTierName.get(),
                    mapping = autoTierMapping.get(),
                    thresholds = autoTierThresholds.get().toList(),
                    recipeStepCost = autoTierRecipeStepCost.get(),
                ),
                preventLobbyChaos = preventLobbyChaos.get(),
                lobbyTutorialBook = lobbyTutorialBook.get(),
                giveMementoInSurvival = giveMementoInSurvival.get(),
                nightVisionInSpectator = nightVisionInSpectator.get(),
                nightVisionInPostgame = nightVisionInPostgame.get(),
                revealAllAdvancements = revealAllAdvancements.get(),
                supportClientHud = supportClientHud.get(),
                startWhenReadySeconds = startWhenReadySeconds.get().takeUnless { it == DISABLED_SECONDS },
                startWhenReadyWaitsForTeams = startWhenReadyWaitsForTeams.get(),
                startWhenReadyWaitsForFirstVote = startWhenReadyWaitsForFirstVote.get(),
                nextRoundWhenReadySeconds = nextRoundWhenReadySeconds.get().takeUnless { it == DISABLED_SECONDS },
                nextRoundWhenReadyWaitsForFirstVote = nextRoundWhenReadyWaitsForFirstVote.get(),
                countdownDelayTicks = countdownDelayTicks.get(),
                countdownSeconds = countdownSeconds.get(),
                nextRoundWhenEveryoneDisconnects = nextRoundWhenEveryoneDisconnects.get(),
                unsafeSkipWorldClose = unsafeSkipWorldClose.get(),
                chat = ChatConfig(
                    defaultToTeamChat = chatDefaultToTeamChat.get(),
                    defaultToSpectatorChat = chatDefaultToSpectatorChat.get(),
                    globalCommandAliases = chatGlobalCommandAliases.get().toList(),
                    teamCommandAliases = chatTeamCommandAliases.get().toList(),
                ),
                databaseUrl = databaseUrl.get().blankToNull(),
                databaseUser = databaseUser.get().blankToNull(),
                databasePass = databasePass.get().blankToNull(),
                syncStats = syncStats.get(),
                statsHostId = UUID.fromString(statsHostId.get()),
                server = ServerConfig(
                    isLobbyMode = serverIsLobbyMode.get(),
                    preloadViewDistance = serverPreloadViewDistance.get(),
                    filesToReset = serverFilesToReset.get().toList(),
                    defaultPlayerSettings = PlayerSettings(
                        seenTutorial = defaultSeenTutorial.get(),
                        hideLobbyPrompt = defaultHideLobbyPrompt.get(),
                        bossbar = defaultBossbar.get(),
                        scoreboard = defaultScoreboard.get(),
                        scoreboardAutoHide = defaultScoreboardAutoHide.get(),
                        leadingMessages = defaultLeadingMessages.get(),
                        scoreMessages = defaultScoreMessages.get(),
                        itemMessages = defaultItemMessages.get(),
                        nightVision = defaultNightVision.get(),
                    ),
                ),
            )

        fun setFrom(config: BingoConfig) {
            itemFilterPresets.set(itemFilterPresetList(config.itemFilterPresets))
            difficultyPresets.set(difficultyPresetList(config.difficultyPresets))
            disabledFilterPresets.set(config.disabledFilterPresets.toList().sorted())
            preventScoringSpawnKitItems.set(config.preventScoringSpawnKitItems)
            excludeSpawnKitItemsFromCards.set(config.excludeSpawnKitItemsFromCards)
            excludeModUncategorizedFromCards.set(config.excludeModUncategorizedFromCards)
            excludeUnbreakableBlocksFromCards.set(config.excludeUnbreakableBlocksFromCards)
            autoTierName.set(config.autoTier.tierListName)
            autoTierMapping.set(config.autoTier.mapping)
            autoTierThresholds.set(config.autoTier.thresholds)
            autoTierRecipeStepCost.set(config.autoTier.recipeStepCost)
            preventLobbyChaos.set(config.preventLobbyChaos)
            lobbyTutorialBook.set(config.lobbyTutorialBook)
            giveMementoInSurvival.set(config.giveMementoInSurvival)
            nightVisionInSpectator.set(config.nightVisionInSpectator)
            nightVisionInPostgame.set(config.nightVisionInPostgame)
            revealAllAdvancements.set(config.revealAllAdvancements)
            supportClientHud.set(config.supportClientHud)
            startWhenReadySeconds.set(config.startWhenReadySeconds ?: DISABLED_SECONDS)
            startWhenReadyWaitsForTeams.set(config.startWhenReadyWaitsForTeams)
            startWhenReadyWaitsForFirstVote.set(config.startWhenReadyWaitsForFirstVote)
            nextRoundWhenReadySeconds.set(config.nextRoundWhenReadySeconds ?: DISABLED_SECONDS)
            nextRoundWhenReadyWaitsForFirstVote.set(config.nextRoundWhenReadyWaitsForFirstVote)
            countdownDelayTicks.set(config.countdownDelayTicks)
            countdownSeconds.set(config.countdownSeconds)
            nextRoundWhenEveryoneDisconnects.set(config.nextRoundWhenEveryoneDisconnects)
            unsafeSkipWorldClose.set(config.unsafeSkipWorldClose)
            chatDefaultToTeamChat.set(config.chat.defaultToTeamChat)
            chatDefaultToSpectatorChat.set(config.chat.defaultToSpectatorChat)
            chatGlobalCommandAliases.set(config.chat.globalCommandAliases)
            chatTeamCommandAliases.set(config.chat.teamCommandAliases)
            databaseUrl.set(config.databaseUrl.orEmpty())
            databaseUser.set(config.databaseUser.orEmpty())
            databasePass.set(config.databasePass.orEmpty())
            syncStats.set(config.syncStats)
            statsHostId.set(config.statsHostId.toString())
            serverIsLobbyMode.set(config.server.isLobbyMode)
            serverPreloadViewDistance.set(config.server.preloadViewDistance)
            serverFilesToReset.set(config.server.filesToReset)
            defaultSeenTutorial.set(config.server.defaultPlayerSettings.seenTutorial)
            defaultHideLobbyPrompt.set(config.server.defaultPlayerSettings.hideLobbyPrompt)
            defaultBossbar.set(config.server.defaultPlayerSettings.bossbar)
            defaultScoreboard.set(config.server.defaultPlayerSettings.scoreboard)
            defaultScoreboardAutoHide.set(config.server.defaultPlayerSettings.scoreboardAutoHide)
            defaultLeadingMessages.set(config.server.defaultPlayerSettings.leadingMessages)
            defaultScoreMessages.set(config.server.defaultPlayerSettings.scoreMessages)
            defaultItemMessages.set(config.server.defaultPlayerSettings.itemMessages)
            defaultNightVision.set(config.server.defaultPlayerSettings.nightVision)
        }
    }

    class ClientValues(builder: ModConfigSpec.Builder) {
        val entries = mutableListOf<ConfigEntry>()
        val sections = mutableListOf<ConfigSection>()

        init {
            builder.pushSection("hud", "Client-side Bingo HUD display settings.", clientSectionKey("hud"), sections)
        }

        val enableHud = builder.defineBoolean(
            "client.enableHud",
            clientKey("hud", "enable_hud"),
            "Renders the client-side Bingo card HUD.",
            "enableHud",
            { defaultConfig().client.enableHud },
            entries,
        )
        val showQuickStartButton = builder.defineBoolean(
            "client.showQuickStartButton",
            clientKey("hud", "show_quick_start_button"),
            "Adds a Bingo button to the title screen for quick single-player setup.",
            "showQuickStartButton",
            { defaultConfig().client.showQuickStartButton },
            entries,
        )
        val cardPausesGame = builder.defineBoolean(
            "client.cardPausesGame",
            clientKey("hud", "card_pauses_game"),
            "Pauses single-player while the Bingo card screen is open.",
            "cardPausesGame",
            { defaultConfig().client.cardPausesGame },
            entries,
        )
        val cardScale = builder.defineDouble(
            "client.cardScale",
            clientKey("hud", "card_scale"),
            "Bingo card HUD scale relative to other GUI elements.",
            "cardScale",
            { defaultConfig().client.cardScale.toDouble() },
            entries,
            min = 0.25,
            max = 4.0,
        )
        val cardAlignment = builder.defineEnum(
            "client.cardAlignment",
            clientKey("hud", "card_alignment"),
            "Screen corner used to anchor the Bingo card HUD.",
            "cardAlignment",
            { defaultConfig().client.cardAlignment },
            entries,
        )
        val cardOffsetX = builder.defineInt(
            "client.cardOffsetX",
            clientKey("hud", "card_offset_x"),
            "Horizontal distance between the Bingo card and its anchored screen edge.",
            "cardOffsetX",
            { defaultConfig().client.cardOffsetX },
            entries,
            min = -10000,
            max = 10000,
        )
        val cardOffsetY = builder.defineInt(
            "client.cardOffsetY",
            clientKey("hud", "card_offset_y"),
            "Vertical distance between the Bingo card and its anchored screen edge.",
            "cardOffsetY",
            { defaultConfig().client.cardOffsetY },
            entries,
            min = -10000,
            max = 10000,
        )
        val cardOverlap = builder.defineEnum(
            "client.cardOverlap",
            clientKey("hud", "card_overlap"),
            "Controls whether the card draws above or underneath other HUD elements.",
            "cardOverlap",
            { defaultConfig().client.cardOverlap },
            entries,
        )
        val cardTeamOutlines = builder.defineBoolean(
            "client.cardTeamOutlines",
            clientKey("hud", "card_team_outlines"),
            "Draws outlines when other teams have captured a card tile.",
            "cardTeamOutlines",
            { defaultConfig().client.cardTeamOutlines },
            entries,
        )
        val showMultipleCards = builder.defineBoolean(
            "client.showMultipleCards",
            clientKey("hud", "show_multiple_cards"),
            "Attempts to draw all available cards in the HUD when space allows.",
            "showMultipleCards",
            { defaultConfig().client.showMultipleCards },
            entries,
        )
        val showItemDifficulties = builder.defineBoolean(
            "client.showItemDifficulties",
            clientKey("hud", "show_item_difficulties"),
            "Draws S/A/B/C/D difficulty letters on item and advancement icons that have an ExBingo tier.",
            "showItemDifficulties",
            { defaultConfig().client.showItemDifficulties },
            entries,
        )
        val hideOnF3 = builder.defineBoolean(
            "client.hideOnF3",
            clientKey("hud", "hide_on_f3"),
            "Hides the Bingo card HUD while the debug screen is open.",
            "hideOnF3",
            { defaultConfig().client.hideOnF3 },
            entries,
        )
        val hideOnChat = builder.defineBoolean(
            "client.hideOnChat",
            clientKey("hud", "hide_on_chat"),
            "Hides the Bingo card HUD while the chat screen is open.",
            "hideOnChat",
            { defaultConfig().client.hideOnChat },
            entries,
        )

        init {
            builder.pop()
            builder.pushSection("messages", "Floating score message display settings.", clientSectionKey("messages"), sections)
        }

        val messageFromOtherTeams = builder.defineBoolean(
            "client.messageFromOtherTeams",
            clientKey("messages", "message_from_other_teams"),
            "Shows item capture messages from other teams.",
            "messageFromOtherTeams",
            { defaultConfig().client.messageFromOtherTeams },
            entries,
        )
        val messageDurationSeconds = builder.defineInt(
            "client.messageDurationSeconds",
            clientKey("messages", "message_duration_seconds"),
            "Seconds each floating message remains visible.",
            "messageDurationSeconds",
            { defaultConfig().client.messageDurationSeconds },
            entries,
            min = 0,
            max = 3600,
        )
        val messageScale = builder.defineDouble(
            "client.messageScale",
            clientKey("messages", "message_scale"),
            "Floating message scale relative to other GUI elements.",
            "messageScale",
            { defaultConfig().client.messageScale.toDouble() },
            entries,
            min = 0.25,
            max = 4.0,
        )

        init {
            builder.pop()
            builder.pushSection("sounds", "Per-sound client notification volumes.", clientSectionKey("sounds"), sections)
        }

        val soundVolumes = builder.defineStringList(
            "client.soundVolumes",
            clientKey("sounds", "sound_volumes"),
            "Per-sound volume overrides written as sound_id=volume.",
            "soundVolumes",
            { soundVolumeList(defaultConfig().client.soundVolumes) },
            entries,
            allowBlank = false,
            validator = ::isSoundVolumeEntry,
        )

        init {
            builder.pop()
        }

        fun applyTo(config: ClientConfig): ClientConfig =
            config.copy(
                enableHud = enableHud.get(),
                showQuickStartButton = showQuickStartButton.get(),
                cardPausesGame = cardPausesGame.get(),
                cardScale = cardScale.get().toFloat(),
                cardAlignment = cardAlignment.get(),
                cardOffsetX = cardOffsetX.get(),
                cardOffsetY = cardOffsetY.get(),
                cardOverlap = cardOverlap.get(),
                cardTeamOutlines = cardTeamOutlines.get(),
                showMultipleCards = showMultipleCards.get(),
                showItemDifficulties = showItemDifficulties.get(),
                hideOnF3 = hideOnF3.get(),
                hideOnChat = hideOnChat.get(),
                messageFromOtherTeams = messageFromOtherTeams.get(),
                messageDurationSeconds = messageDurationSeconds.get(),
                messageScale = messageScale.get().toFloat(),
                soundVolumes = parseSoundVolumeList(soundVolumes.get()),
            )

        fun setFrom(config: ClientConfig) {
            enableHud.set(config.enableHud)
            showQuickStartButton.set(config.showQuickStartButton)
            cardPausesGame.set(config.cardPausesGame)
            cardScale.set(config.cardScale.toDouble())
            cardAlignment.set(config.cardAlignment)
            cardOffsetX.set(config.cardOffsetX)
            cardOffsetY.set(config.cardOffsetY)
            cardOverlap.set(config.cardOverlap)
            cardTeamOutlines.set(config.cardTeamOutlines)
            showMultipleCards.set(config.showMultipleCards)
            showItemDifficulties.set(config.showItemDifficulties)
            hideOnF3.set(config.hideOnF3)
            hideOnChat.set(config.hideOnChat)
            messageFromOtherTeams.set(config.messageFromOtherTeams)
            messageDurationSeconds.set(config.messageDurationSeconds)
            messageScale.set(config.messageScale.toDouble())
            soundVolumes.set(soundVolumeList(config.soundVolumes))
        }
    }

    data class ConfigEntry(
        val configPath: String,
        val translationKey: String,
    )

    data class ConfigSection(
        val translationKey: String,
    )

    private fun commonSectionKey(section: String) = "$MOD_ID_BINGO.configuration.common.$section"
    private fun clientSectionKey(section: String) = "$MOD_ID_BINGO.configuration.client.$section"
    private fun commonKey(section: String, name: String) = "${commonSectionKey(section)}.$name"
    private fun clientKey(section: String, name: String) = "${clientSectionKey(section)}.$name"

    private fun ModConfigSpec.Builder.pushSection(
        path: String,
        comment: String,
        translationKey: String,
        sections: MutableList<ConfigSection>,
    ) {
        sections += ConfigSection(translationKey)
        comment(comment).translation(translationKey).push(path)
    }

    private fun ModConfigSpec.Builder.value(
        configPath: String,
        translationKey: String,
        comment: String,
        entries: MutableList<ConfigEntry>,
    ): ModConfigSpec.Builder {
        entries += ConfigEntry(configPath, translationKey)
        return comment(comment).translation(translationKey)
    }

    private fun ModConfigSpec.Builder.defineBoolean(
        configPath: String,
        translationKey: String,
        comment: String,
        tomlPath: String,
        defaultValue: () -> Boolean,
        entries: MutableList<ConfigEntry>,
    ): ModConfigSpec.BooleanValue =
        value(configPath, translationKey, comment, entries)
            .define(listOf(tomlPath), Supplier { defaultValue() })

    private fun ModConfigSpec.Builder.defineInt(
        configPath: String,
        translationKey: String,
        comment: String,
        tomlPath: String,
        defaultValue: () -> Int,
        entries: MutableList<ConfigEntry>,
        min: Int,
        max: Int,
    ): ModConfigSpec.ConfigValue<Int> =
        value(configPath, translationKey, "$comment Range: $min ~ $max.", entries)
            .define(listOf(tomlPath), Supplier { defaultValue() }, Predicate { it is Number && it.toInt() in min..max }, Int::class.java)

    private fun ModConfigSpec.Builder.defineDouble(
        configPath: String,
        translationKey: String,
        comment: String,
        tomlPath: String,
        defaultValue: () -> Double,
        entries: MutableList<ConfigEntry>,
        min: Double,
        max: Double,
    ): ModConfigSpec.ConfigValue<Double> =
        value(configPath, translationKey, "$comment Range: $min ~ $max.", entries)
            .define(listOf(tomlPath), Supplier { defaultValue() }, Predicate { it is Number && it.toDouble() in min..max }, Double::class.java)

    private fun ModConfigSpec.Builder.defineString(
        configPath: String,
        translationKey: String,
        comment: String,
        tomlPath: String,
        defaultValue: () -> String,
        entries: MutableList<ConfigEntry>,
        allowBlank: Boolean,
        validator: (String) -> Boolean = { true },
    ): ModConfigSpec.ConfigValue<String> =
        value(configPath, translationKey, comment, entries)
            .define(
                listOf(tomlPath),
                Supplier { defaultValue() },
                Predicate { it is String && (allowBlank || it.isNotBlank()) && validator(it) },
                String::class.java,
            )

    private inline fun <reified T> ModConfigSpec.Builder.defineEnum(
        configPath: String,
        translationKey: String,
        comment: String,
        tomlPath: String,
        noinline defaultValue: () -> T,
        entries: MutableList<ConfigEntry>,
    ): ModConfigSpec.EnumValue<T> where T : Enum<T> =
        value(configPath, translationKey, comment, entries)
            .defineEnum(
                listOf(tomlPath),
                Supplier { defaultValue() },
                Predicate { value ->
                    value is T || (value is String && enumValues<T>().any { it.name.equals(value, ignoreCase = true) })
                },
                T::class.java,
            )

    private fun ModConfigSpec.Builder.defineStringList(
        configPath: String,
        translationKey: String,
        comment: String,
        tomlPath: String,
        defaultValue: () -> List<String>,
        entries: MutableList<ConfigEntry>,
        allowBlank: Boolean,
        validator: (String) -> Boolean = { true },
    ): ModConfigSpec.ConfigValue<List<String>> =
        value(configPath, translationKey, comment, entries)
            .defineList(
                listOf(tomlPath),
                Supplier<List<String>> { defaultValue() },
                Supplier { "" },
                Predicate { it is String && (allowBlank || it.isNotBlank()) && validator(it) },
            ) as ModConfigSpec.ConfigValue<List<String>>

    private fun ModConfigSpec.Builder.defineDoubleList(
        configPath: String,
        translationKey: String,
        comment: String,
        tomlPath: String,
        defaultValue: () -> List<Double>,
        entries: MutableList<ConfigEntry>,
        min: Double,
        max: Double,
    ): ModConfigSpec.ConfigValue<List<Double>> =
        value(configPath, translationKey, "$comment Each value must be in range $min ~ $max.", entries)
            .defineList(
                listOf(tomlPath),
                Supplier<List<Double>> { defaultValue() },
                Supplier { 0.0 },
                Predicate { it is Number && it.toDouble() in min..max },
            ) as ModConfigSpec.ConfigValue<List<Double>>

    private fun String.blankToNull(): String? = takeIf { it.isNotBlank() }

    private fun itemFilterPresetList(presets: Map<String, ObjectiveFilterList>): List<String> =
        presets.entries
            .sortedBy { it.key }
            .map { (name, filter) -> "$name=$filter" }

    private fun parseItemFilterPresetList(values: List<String>): Map<String, ObjectiveFilterList> =
        values.mapNotNull { value ->
            val name = value.substringBefore('=').trim()
            val filter = value.substringAfter('=', "").trim()
            if (name.isNotBlank()) {
                name to ObjectiveFilterList.fromString(filter)
            } else {
                null
            }
        }.toMap(LinkedHashMap()).ifEmpty { ObjectiveFilterList.PRESETS }

    private fun isItemFilterPresetEntry(value: String): Boolean =
        value.substringBefore('=').trim().isNotBlank() && value.contains('=')

    private fun difficultyPresetList(presets: Map<String, List<Int>>): List<String> =
        presets.entries
            .sortedBy { it.key }
            .map { (name, distribution) -> "$name=${distribution.joinToString(",")}" }

    private fun parseDifficultyPresetList(values: List<String>): Map<String, List<Int>> =
        values.mapNotNull { value ->
            val name = value.substringBefore('=').trim()
            val distribution = parseDifficultyDistribution(value.substringAfter('=', ""))
            if (name.isNotBlank() && distribution != null) {
                name to distribution
            } else {
                null
            }
        }.toMap(LinkedHashMap()).ifEmpty { TierLabel.DIFFICULTY_PRESETS }

    private fun isDifficultyPresetEntry(value: String): Boolean {
        val name = value.substringBefore('=').trim()
        val distribution = parseDifficultyDistribution(value.substringAfter('=', ""))
        return name.isNotBlank() && distribution != null
    }

    private fun parseDifficultyDistribution(value: String): List<Int>? {
        val distribution = value.split(',')
            .map { it.trim().toIntOrNull() ?: return null }
        return distribution.takeIf {
            it.size == TierLabel.entries.size &&
                    it.all { count -> count >= 0 } &&
                    it.sum() == 25
        }
    }

    private fun soundVolumeList(soundVolumes: Map<String, Float>): List<String> =
        soundVolumes.entries
            .sortedBy { it.key }
            .map { (sound, volume) -> "$sound=$volume" }

    private fun parseSoundVolumeList(values: List<String>): MutableMap<String, Float> =
        values.mapNotNull { value ->
            val sound = value.substringBefore('=').trim()
            val volume = value.substringAfter('=', "").trim().toFloatOrNull()
            if (sound.isNotBlank() && volume != null && volume in 0f..1f) {
                sound to volume
            } else {
                null
            }
        }.toMap(LinkedHashMap()).toMutableMap()

    private fun isSoundVolumeEntry(value: String): Boolean {
        val sound = value.substringBefore('=').trim()
        val volume = value.substringAfter('=', "").trim().toFloatOrNull()
        return sound.isNotBlank() && volume != null && volume in 0f..1f
    }
}
