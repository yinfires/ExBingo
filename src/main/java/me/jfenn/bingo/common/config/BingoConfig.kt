package me.jfenn.bingo.common.config

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import me.jfenn.bingo.common.card.filter.ObjectiveFilter
import me.jfenn.bingo.common.card.filter.ObjectiveFilterList
import me.jfenn.bingo.common.card.autotier.AutoTierConfig
import me.jfenn.bingo.common.card.tierlist.TierLabel
import me.jfenn.bingo.platform.utils.UuidAsString
import java.util.*

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class BingoConfig(
    var version: Int = 0,

    var itemFilterPresets: Map<String, ObjectiveFilterList> = ObjectiveFilterList.PRESETS,
    var difficultyPresets: Map<String, List<Int>> = TierLabel.DIFFICULTY_PRESETS,
    // source ids are tier list file names (plus generated sources such as uncategorized).
    // Missing sources default to 1.0 at card-generation time.
    var boardSourceWeights: Map<String, Double> = DEFAULT_BOARD_SOURCE_WEIGHTS,

    // ids of filter presets ("boards") that ops have disabled. Disabled boards are hidden
    // from the board-selection menu and never returned by the preset list, so they can't be
    // picked individually or via any "select all" enumeration. Managed by /bingo cardenable
    // & /bingo carddisable; persisted server-side in config.json.
    var disabledFilterPresets: Set<String> = emptySet(),

    // adds BINGO_IGNORE to spawn kit items, preventing them from being scored
    val preventScoringSpawnKitItems: Boolean = false,
    // excludes the active spawn kit items from item tiers when generating a card
    val excludeSpawnKitItemsFromCards: Boolean = true,

    // when true, uncategorized objectives from non-vanilla (modded) namespaces are NOT
    // added to cards by default. Vanilla (minecraft:) uncategorized content is unaffected
    // and keeps the original behavior. Players can still opt in explicitly via the
    // 'uncategorized' / 'from=<modid>' filters.
    val excludeModUncategorizedFromCards: Boolean = true,
    // when true, items whose placed block is unbreakable (bedrock, barrier, command
    // blocks, structure blocks, ...) are never added to cards by default.
    val excludeUnbreakableBlocksFromCards: Boolean = true,

    // configuration for the /bingo autotier auto-classification feature
    val autoTier: AutoTierConfig = AutoTierConfig(),

    // stops sounds from being sent to other players in the lobby
    val preventLobbyChaos: Boolean = false,
    // gives players a tutorial/settings book when they join the lobby
    val lobbyTutorialBook: Boolean = true,

    // team shared chest and same-team teleport features
    var teamChestEnabled: Boolean = true,
    var teamChestCountsForObjectives: Boolean = true,
    var teamTeleportEnabled: Boolean = true,

    // gives players a "memento" card after the game ends (if isLobbyMode=false)
    val giveMementoInSurvival: Boolean = true,

    val nightVisionInSpectator: Boolean = true,
    val nightVisionInPostgame: Boolean = true,

    // when true, the entire vanilla advancement tree (the "L" screen) is shown
    // from the start, instead of hiding advancements until their prerequisites
    // are unlocked. This only affects visibility/display, not actual progress.
    val revealAllAdvancements: Boolean = true,

    // whether the server should allow connecting clients to use the bingo HUD
    // instead of the vanilla map/card behavior
    var supportClientHud: Boolean = true,

    // starts the game after the time limit - reduced when players vote /ready
    val startWhenReadySeconds: Int? = 600,
    val startWhenReadyWaitsForTeams: Boolean = true,
    val startWhenReadyWaitsForFirstVote: Boolean = false,
    // resets after a time limit - reduced when players vote /ready
    val nextRoundWhenReadySeconds: Int? = 600,
    val nextRoundWhenReadyWaitsForFirstVote: Boolean = true,

    // amount of time to wait before starting the game
    val countdownDelayTicks: Int = 100,
    val countdownSeconds: Int = 3,

    // resets the server once all players log out after a game
    @JsonNames("autoShutDownInPostgame", "shutDownWhenEveryoneDisconnects")
    val nextRoundWhenEveryoneDisconnects: Boolean = true,
    // shuts down the server faster, at the risk of bugs or data corruption
    val unsafeSkipWorldClose: Boolean = false,

    val chat: ChatConfig = ChatConfig(),

    val databaseUrl: String? = null,
    val databaseUser: String? = null,
    val databasePass: String? = null,

    // whether to sync game stats with any connected servers/clients
    var syncStats: Boolean = true,
    // the "host_id" stored in stats.db to distinguish games synced vs. played on a server
    val statsHostId: UuidAsString = UUID.randomUUID(),

    // only applies to client
    val client: ClientConfig = ClientConfig(),

    // only applies to dedicated server
    val server: ServerConfig = ServerConfig(),
) {
    init {
        difficultyPresets = TierLabel.normalizeDefaultDifficultyPresetOrder(difficultyPresets)
    }

    companion object {
        val DEFAULT_BOARD_SOURCE_WEIGHTS = linkedMapOf(
            "advancements" to 1.0,
            "cataclysm" to 1.0,
            "challenge" to 1.0,
            "eternal_starlight" to 1.0,
            "farmersdelight" to 1.0,
            "fdbosses" to 1.0,
            "iceandfire" to 1.0,
            "irons_spellbooks" to 1.0,
            "items" to 1.0,
            "mowziesmobs" to 1.0,
            "netherman" to 1.0,
            "simplified" to 1.0,
            "the_bumblezone" to 1.0,
            "twilightforest" to 1.0,
            "undergarden" to 1.0,
            AutoTierConfig().tierListName to 1.0,
            ObjectiveFilter.UNCATEGORIZED to 1.0,
        )
    }
}
