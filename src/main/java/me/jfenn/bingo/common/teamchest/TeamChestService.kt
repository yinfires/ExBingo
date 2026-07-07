package me.jfenn.bingo.common.teamchest

import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.team.BingoTeam
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.common.team.TeamService
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.inventory.IContainerItemView
import me.jfenn.bingo.platform.item.IItemStack
import me.jfenn.bingo.platform.item.IItemStackFactory
import net.minecraft.ChatFormatting
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack

internal const val TEAM_CHEST_ROWS = 3
internal const val TEAM_CHEST_SIZE = TEAM_CHEST_ROWS * 9

internal class TeamChestService(
    private val config: BingoConfig,
    private val state: BingoState,
    private val teamService: TeamService,
    private val playerManager: IPlayerManager,
    private val itemStackFactory: IItemStackFactory,
    private val text: TextProvider,
) {
    private val containers = mutableMapOf<BingoTeamKey, TeamChestContainer>()

    fun openTeamChest(player: IPlayerHandle) {
        if (!config.teamChestEnabled) {
            player.sendMessage(text.string(StringKey.CommandTeamChestDisabled).formatted(ChatFormatting.RED))
            return
        }

        if (state.state != GameState.PLAYING) {
            player.sendMessage(text.string(StringKey.CommandTeamChestGameNotStarted).formatted(ChatFormatting.RED))
            return
        }

        val team = teamService.getPlayerTeam(player)
        if (team == null) {
            player.sendMessage(text.string(StringKey.CommandTeamChestNoTeam).formatted(ChatFormatting.RED))
            return
        }

        val inventory = getContainer(team)
        player.player.openMenu(
            SimpleMenuProvider(
                { syncId, inv, _ ->
                    ChestMenu(
                        MenuType.GENERIC_9x3,
                        syncId,
                        inv,
                        inventory,
                        TEAM_CHEST_ROWS,
                    )
                },
                text.string(StringKey.TeamChestContainer).value,
            )
        )
    }

    fun shouldExposeScoredItems(team: BingoTeam, player: IPlayerHandle): Boolean {
        return config.teamChestEnabled
            && config.teamChestCountsForObjectives
            && state.state == GameState.PLAYING
            && team.includesPlayer(player)
            && playerManager.getPlayer(player.uuid) != null
    }

    fun getScoredStacks(team: BingoTeam): List<IItemStack> {
        if (!config.teamChestEnabled || !config.teamChestCountsForObjectives || state.state != GameState.PLAYING)
            return emptyList()

        val container = getContainer(team)
        return (0 until container.containerSize)
            .asSequence()
            .map { itemStackFactory.forStack(container.getItem(it)) }
            .filter { !it.isEmpty }
            .toList()
    }

    fun getScoredStackViews(player: IPlayerHandle): Sequence<IContainerItemView> {
        if (!config.teamChestEnabled || !config.teamChestCountsForObjectives || state.state != GameState.PLAYING)
            return emptySequence()

        if (playerManager.getPlayer(player.uuid) == null)
            return emptySequence()

        val team = teamService.getPlayerTeam(player) ?: return emptySequence()
        val container = getContainer(team)
        return (0 until container.containerSize)
            .asSequence()
            .map { TeamChestItemView(container, it) }
            .filter { !it.stack.isEmpty }
    }

    fun clearAll() {
        state.teamChests.clear()
        clearCache()
    }

    fun clearCache() {
        containers.values.forEach { it.clearWithoutSaving() }
        containers.clear()
    }

    private fun getContainer(team: BingoTeam): TeamChestContainer {
        return containers.getOrPut(team.key) {
            val data = state.teamChests.getOrPut(team.key) { TeamChestData() }
            TeamChestContainer(team.key, itemStackFactory, ::saveContainer).apply {
                load(data.stacks)
            }
        }
    }

    private fun saveContainer(key: BingoTeamKey, container: TeamChestContainer) {
        state.teamChests[key] = TeamChestData(
            (0 until container.containerSize)
                .map { itemStackFactory.forStack(container.getItem(it).copy()) }
                .toMutableList()
        )
    }

    private class TeamChestContainer(
        private val teamKey: BingoTeamKey,
        private val itemStackFactory: IItemStackFactory,
        private val save: (BingoTeamKey, TeamChestContainer) -> Unit,
    ) : SimpleContainer(TEAM_CHEST_SIZE) {
        private var suppressSave = false

        fun load(stacks: List<IItemStack>) {
            suppressSave = true
            for (slot in 0 until containerSize) {
                setItem(slot, stacks.getOrNull(slot)?.stack?.copy() ?: ItemStack.EMPTY)
            }
            suppressSave = false
        }

        fun clearWithoutSaving() {
            suppressSave = true
            clearContent()
            suppressSave = false
        }

        override fun setChanged() {
            super.setChanged()
            if (!suppressSave) {
                save(teamKey, this)
            }
        }

        fun stackAt(slot: Int): IItemStack = itemStackFactory.forStack(getItem(slot))
    }

    private class TeamChestItemView(
        private val container: TeamChestContainer,
        private val slot: Int,
    ) : IContainerItemView {
        override val stack: IItemStack
            get() = container.stackAt(slot)

        override fun replace(newStack: IItemStack) {
            container.setItem(slot, newStack.stack.takeUnless { it.isEmpty } ?: ItemStack.EMPTY)
        }
    }
}
