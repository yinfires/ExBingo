package me.jfenn.bingo.common.teamchest

import me.jfenn.bingo.common.data.ScopedData
import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.spawn.ChestService
import me.jfenn.bingo.common.spawn.PlayerController
import me.jfenn.bingo.common.spawn.SpawnService
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.team.BingoTeam
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.common.team.TeamService
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.IExecutors
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.PlayerSoundCategory
import me.jfenn.bingo.platform.PlayerSoundEvent
import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.text.TextAction
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.slf4j.Logger
import java.util.UUID
import kotlin.math.ceil

private const val TEAM_JOIN_ROWS = 3
private const val TEAM_JOIN_SIZE = TEAM_JOIN_ROWS * 9

internal class SpectatorTeamJoinService(
    events: ScopedEvents,
    private val state: BingoState,
    private val data: ScopedData,
    private val teamService: TeamService,
    private val playerManager: IPlayerManager,
    private val spawnService: SpawnService,
    private val playerController: PlayerController,
    private val chestService: ChestService,
    private val taskExecutor: IExecutors.IServerTaskExecutor,
    private val log: Logger,
    private val text: TextProvider,
) {
    private val requests = mutableMapOf<RequestKey, JoinRequest>()
    private val joiningPlayers = mutableSetOf<UUID>()

    init {
        events.onChangeTeam { event ->
            val joinedTeam = event.team
            if (joinedTeam != null) {
                joiningPlayers.remove(event.player.uuid)
                completeOtherRequests(event.player, joinedTeam)
            }

            refreshPendingRequests()
        }

        events.onPlayerDisconnect { event ->
            expireRequesterRequests(event.player.uuid)
            pruneOfflineApprovals(event.player.uuid)
            joiningPlayers.remove(event.player.uuid)
            refreshPendingRequests()
        }

        events.onPlayerJoin {
            refreshPendingRequests()
        }

        events.onStateChange { (_, to) ->
            if (to != GameState.PLAYING) {
                requests.clear()
                joiningPlayers.clear()
            }
        }

    }

    fun openTeamSelection(player: IPlayerHandle): Boolean {
        if (!canUseSelection(player)) {
            return false
        }

        player.player.openMenu(
            SimpleMenuProvider(
                { syncId, inv, _ ->
                    TeamSelectionMenu(
                        syncId = syncId,
                        playerInventory = inv,
                        inventory = TeamSelectionInventory(),
                        service = this,
                        viewerId = player.uuid,
                        teamKeys = teamKeys(),
                    )
                },
                serverText("选择队伍").value,
            )
        )
        return true
    }

    private fun canUseSelection(player: IPlayerHandle): Boolean {
        return player.uuid !in joiningPlayers && canRequesterJoinNow(player)
    }

    private fun canRequesterJoinNow(player: IPlayerHandle): Boolean {
        return state.state == GameState.PLAYING &&
            teamService.getPlayerTeam(player) == null &&
            (state.playersSpectatingIds.contains(player.uuid) || player.isSpectator)
    }

    private fun getTeamTemplate(teamKey: BingoTeamKey): BingoTeam? {
        val preset = data.teamPresets[teamKey] ?: return null
        return BingoTeam.fromPreset(teamKey, preset)
    }

    private fun onlineTeamPlayers(teamKey: BingoTeamKey): List<IPlayerHandle> {
        return playerManager.getPlayers()
            .filter { teamService.getPlayerTeam(it)?.key == teamKey }
    }

    private fun onlineTeamSize(teamKey: BingoTeamKey): Int {
        return onlineTeamPlayers(teamKey).size
    }

    fun playerFor(player: Player): IPlayerHandle {
        val serverPlayer = player as? net.minecraft.server.level.ServerPlayer
            ?: error("Team selection can only be used by server players")
        return playerManager.forPlayer(serverPlayer)
    }

    fun teamKeys(): List<BingoTeamKey> {
        return data.teamPresets.keys.toList()
    }

    fun teamItemStacks(): List<ItemStack> {
        val teams = teamKeys()
        return List(TEAM_JOIN_SIZE) { slot ->
            val teamKey = teams.getOrNull(slot) ?: return@List ItemStack.EMPTY
            val team = getTeamTemplate(teamKey) ?: return@List ItemStack.EMPTY
            createTeamStack(team, onlineTeamSize(teamKey))
        }
    }

    private fun createTeamStack(team: BingoTeam, count: Int): ItemStack {
        val stack = ItemStack(woolItem(team.textColor))
        val teamName = serverTeamName(team, bracketed = false).formatted(team.textColor)
        val countText = serverText("当前人数：$count").formatted(team.textColor)
        val hint = serverText("点击后申请加入队伍").formatted(team.textColor)

        stack[DataComponents.CUSTOM_NAME] = teamName.value
        stack[DataComponents.LORE] = ItemLore(listOf(countText.value, hint.value))
        return stack
    }

    private fun woolItem(color: ChatFormatting) = when (color) {
        ChatFormatting.AQUA -> Items.CYAN_WOOL
        ChatFormatting.BLUE,
        ChatFormatting.DARK_BLUE -> Items.BLUE_WOOL
        ChatFormatting.GRAY,
        ChatFormatting.DARK_GRAY -> Items.LIGHT_GRAY_WOOL
        ChatFormatting.GREEN,
        ChatFormatting.DARK_GREEN -> Items.GREEN_WOOL
        ChatFormatting.GOLD -> Items.ORANGE_WOOL
        ChatFormatting.YELLOW -> Items.YELLOW_WOOL
        ChatFormatting.LIGHT_PURPLE,
        ChatFormatting.DARK_PURPLE -> Items.PINK_WOOL
        ChatFormatting.RED,
        ChatFormatting.DARK_RED -> Items.RED_WOOL
        else -> Items.WHITE_WOOL
    }

    fun requestJoin(requester: IPlayerHandle, teamKey: BingoTeamKey) {
        log.info(
            "Spectator team join request: {} ({}) -> {}",
            requester.playerName,
            requester.uuid,
            teamKey.id,
        )

        if (!canUseSelection(requester)) {
            requester.sendMessage(serverText("只有在对局进行中且为旁观者时才能申请加入队伍。").formatted(ChatFormatting.RED))
            requester.player.closeContainer()
            return
        }

        val team = getTeamTemplate(teamKey)
        if (team == null) {
            requester.sendMessage(serverText("该队伍已经不存在，请重新打开队伍选择。").formatted(ChatFormatting.RED))
            requester.player.closeContainer()
            return
        }

        val teamPlayers = onlineTeamPlayers(teamKey)
        if (teamPlayers.isEmpty()) {
            requester.sendMessage(serverText("正在加入").append(serverTeamName(team)).append("……").formatted(ChatFormatting.YELLOW))
            joinApprovedTeam(requester, team)
            return
        }

        val request = requests.getOrPut(RequestKey(requester.uuid, teamKey)) {
            JoinRequest(requester.uuid, requester.playerName, teamKey)
        }
        request.requesterName = requester.playerName
        request.pruneApprovals(teamPlayers.map { it.uuid }.toSet())

        sendRequestMessages(request, team, teamPlayers)
        requester.sendMessage(serverText("已向").append(serverTeamName(team)).append("发送入队申请。").formatted(ChatFormatting.YELLOW))
        requester.player.closeContainer()
    }

    fun approve(approver: IPlayerHandle, requesterId: UUID, teamKey: BingoTeamKey) {
        val request = requests[RequestKey(requesterId, teamKey)]
        if (request == null) {
            approver.sendMessage(serverText("该入队申请已经失效。").formatted(ChatFormatting.RED))
            return
        }

        val team = getTeamTemplate(teamKey)
        if (team == null) {
            requests.remove(request.key)
            approver.sendMessage(serverText("该入队申请已经失效。").formatted(ChatFormatting.RED))
            return
        }

        if (teamService.getPlayerTeam(approver)?.key != teamKey) {
            approver.sendMessage(serverText("你已经不在该队伍中。").formatted(ChatFormatting.RED))
            return
        }

        val teamPlayers = onlineTeamPlayers(teamKey)
        request.pruneApprovals(teamPlayers.map { it.uuid }.toSet())
        request.approvals += approver.uuid

        val requester = playerManager.getPlayer(requesterId)
        if (requester == null) {
            expireRequest(request)
            return
        }

        val currentTeam = teamService.getPlayerTeam(requester)
        if (currentTeam != null) {
            expireAsJoined(request, currentTeam)
            return
        }

        if (requester.uuid in joiningPlayers) {
            approver.sendMessage(serverText("该入队申请已经失效。").formatted(ChatFormatting.RED))
            return
        }

        if (!canRequesterJoinNow(requester)) {
            expireRequest(request)
            return
        }

        if (tryCompleteRequest(request, team, requester, teamPlayers)) {
            return
        }

        sendRequestMessages(request, team, teamPlayers)
    }

    private fun approvalsNeeded(teamSize: Int): Int {
        return ceil(teamSize / 2.0).toInt().coerceAtLeast(1)
    }

    private fun joinApprovedTeam(
        player: IPlayerHandle,
        teamTemplate: BingoTeam,
    ) {
        if (!canUseSelection(player)) {
            return
        }

        val playerId = player.uuid
        val playerName = player.playerName
        joiningPlayers += playerId
        player.player.closeContainer()

        val team = state.registerTeam(teamTemplate)
        val createSpawnpointFuture = spawnService.getTeamSpawnpointAsync(team)
        createSpawnpointFuture.whenCompleteAsync({ _, error ->
            try {
                val currentPlayer = playerManager.getPlayer(playerId)
                if (error != null) {
                    log.warn("Failed to join spectator {} to team {}", playerName, team.key.id, error)
                    currentPlayer?.sendMessage(serverText("加入队伍失败，请重新申请。").formatted(ChatFormatting.RED))
                    return@whenCompleteAsync
                }

                if (currentPlayer == null || playerId !in joiningPlayers || !canRequesterJoinNow(currentPlayer)) {
                    return@whenCompleteAsync
                }

                teamService.joinTeam(currentPlayer, team)
                playerController.updateGameMode(currentPlayer, forceReset = true)

                if (team.chestSpawnpoint == null) {
                    chestService.createChestBlock(team)
                }

                currentPlayer.playSound(PlayerSoundEvent.ENTITY_PLAYER_LEVELUP, PlayerSoundCategory.MAIN, 1f, 1f)
            } finally {
                joiningPlayers.remove(playerId)
            }
        }, taskExecutor)
    }

    private fun tryCompleteRequest(
        request: JoinRequest,
        team: BingoTeam,
        requester: IPlayerHandle,
        teamPlayers: List<IPlayerHandle>,
    ): Boolean {
        if (teamPlayers.isNotEmpty() && request.approvals.size < approvalsNeeded(teamPlayers.size)) {
            return false
        }

        request.approved = true
        requester.sendMessage(serverText("正在加入").append(serverTeamName(team)).append("……").formatted(ChatFormatting.YELLOW))
        joinApprovedTeam(requester, team)
        return true
    }

    private fun sendRequestMessages(request: JoinRequest, team: BingoTeam, recipients: List<IPlayerHandle>) {
        val teamSize = recipients.size
        for (recipient in recipients) {
            recipient.sendMessage(requestMessage(request, team, teamSize, recipient.uuid))
        }
    }

    private fun requestMessage(
        request: JoinRequest,
        team: BingoTeam,
        teamSize: Int,
        recipientId: UUID,
    ): IText {
        val action = if (recipientId in request.approvals) {
            serverText("已同意").formatted(ChatFormatting.GRAY)
        } else {
            serverText("点击同意")
                .formatted(ChatFormatting.GREEN, ChatFormatting.UNDERLINE)
                .also {
                    it.setClickEvent(
                        TextAction.RunCommand(
                            "/bingo teamrequest accept ${request.requesterId} ${request.teamKey.id}"
                        )
                    )
                }
        }

        return text.empty()
            .append(text.literal(request.requesterName).formatted(ChatFormatting.AQUA))
            .append(serverText("玩家想加入你们的队伍"))
            .append(action.bracketed())
            .append(text.literal("(${request.approvals.size}/$teamSize)").formatted(ChatFormatting.YELLOW))
    }

    private fun notifyTeamRequestJoined(
        request: JoinRequest,
        team: BingoTeam,
        recipients: List<IPlayerHandle>,
    ) {
        val message = text.empty()
            .append(text.literal(request.requesterName).formatted(ChatFormatting.AQUA))
            .append(serverText("已加入").formatted(ChatFormatting.YELLOW))
            .append(serverTeamName(team))

        for (recipient in recipients) {
            recipient.sendMessage(message.copy())
        }
    }

    private fun completeOtherRequests(player: IPlayerHandle, joinedTeam: BingoTeam) {
        val requestKeys = requests.keys
            .filter { it.requesterId == player.uuid }
        if (requestKeys.isEmpty()) return

        val messageRequests = requestKeys.mapNotNull { requests.remove(it) }
        for (request in messageRequests) {
            val recipients = onlineTeamPlayers(request.teamKey)
            notifyTeamRequestJoined(request, joinedTeam, recipients)
        }
    }

    private fun refreshPendingRequests() {
        val handledRequesters = mutableSetOf<UUID>()
        for (request in requests.values.toList()) {
            if (request.key !in requests) continue
            if (request.approved) continue

            val requester = playerManager.getPlayer(request.requesterId)
            if (requester == null) {
                expireRequest(request)
                continue
            }

            val currentTeam = teamService.getPlayerTeam(requester)
            if (currentTeam != null) {
                expireAsJoined(request, currentTeam)
                continue
            }

            if (requester.uuid in joiningPlayers) {
                continue
            }

            if (!canRequesterJoinNow(requester)) {
                expireRequest(request)
                continue
            }

            val team = getTeamTemplate(request.teamKey)
            if (team == null) {
                requests.remove(request.key)
                continue
            }

            val teamPlayers = onlineTeamPlayers(request.teamKey)
            request.pruneApprovals(teamPlayers.map { it.uuid }.toSet())

            if (request.requesterId !in handledRequesters &&
                tryCompleteRequest(request, team, requester, teamPlayers)
            ) {
                handledRequesters += request.requesterId
                continue
            }

            if (teamPlayers.isNotEmpty()) {
                sendRequestMessages(request, team, teamPlayers)
            }
        }
    }

    private fun expireRequesterRequests(requesterId: UUID) {
        val removedRequests = requests.keys
            .filter { it.requesterId == requesterId }
            .mapNotNull { requests.remove(it) }

        for (request in removedRequests) {
            expireRequest(request, remove = false)
        }
    }

    private fun expireAsJoined(request: JoinRequest, joinedTeam: BingoTeam) {
        requests.remove(request.key)
        notifyTeamRequestJoined(request, joinedTeam, onlineTeamPlayers(request.teamKey))
    }

    private fun expireRequest(request: JoinRequest, remove: Boolean = true) {
        if (remove) {
            requests.remove(request.key)
        }
        val message = serverText("该入队申请已经失效。").formatted(ChatFormatting.YELLOW)

        for (recipient in onlineTeamPlayers(request.teamKey)) {
            recipient.sendMessage(message.copy())
        }
    }

    private fun serverText(value: String): IText {
        return text.literal(value)
    }

    private fun serverTeamName(team: BingoTeam, bracketed: Boolean = true): IText {
        val name = when (team.key.id) {
            "bingo_aqua" -> "青队"
            "bingo_blue" -> "蓝队"
            "bingo_gray" -> "灰队"
            "bingo_green" -> "绿队"
            "bingo_orange" -> "橙队"
            "bingo_pink" -> "粉队"
            "bingo_red" -> "红队"
            "bingo_yellow" -> "黄队"
            else -> return team.getName(text, bracketed = bracketed)
        }
        val teamName = serverText(name).formatted(team.textColor)
        return if (bracketed) teamName.bracketed() else teamName
    }

    private fun pruneOfflineApprovals(playerId: UUID) {
        for (request in requests.values) {
            request.approvals.remove(playerId)
        }
    }

    private data class RequestKey(
        val requesterId: UUID,
        val teamKey: BingoTeamKey,
    )

    private class JoinRequest(
        val requesterId: UUID,
        var requesterName: String,
        val teamKey: BingoTeamKey,
    ) {
        val key = RequestKey(requesterId, teamKey)
        val approvals = mutableSetOf<UUID>()
        var approved = false

        fun pruneApprovals(onlineTeamPlayerIds: Set<UUID>) {
            approvals.retainAll(onlineTeamPlayerIds)
        }
    }
}

private class TeamSelectionInventory : SimpleContainer(TEAM_JOIN_SIZE)

private class TeamSelectionMenu(
    syncId: Int,
    playerInventory: Inventory,
    private val inventory: TeamSelectionInventory,
    private val service: SpectatorTeamJoinService,
    private val viewerId: UUID,
    private val teamKeys: List<BingoTeamKey>,
) : AbstractContainerMenu(MenuType.GENERIC_9x3, syncId), SpectatorClickableMenu {
    init {
        for (row in 0 until TEAM_JOIN_ROWS) {
            for (col in 0 until 9) {
                val index = col + row * 9
                addSlot(DisplaySlot(inventory, index, 8 + col * 18, 18 + row * 18))
            }
        }

        val offset = TEAM_JOIN_ROWS * 18 + 12
        for (row in 0 until 3) {
            for (col in 0 until 9) {
                addSlot(DisplaySlot(playerInventory, col + row * 9 + 9, 8 + col * 18, offset + row * 18))
            }
        }

        for (col in 0 until 9) {
            addSlot(DisplaySlot(playerInventory, col, 8 + col * 18, offset + 58))
        }

        refreshItems()
    }

    private fun refreshItems() {
        service.teamItemStacks().forEachIndexed { index, stack ->
            inventory.setItem(index, stack)
        }
    }

    override fun broadcastChanges() {
        refreshItems()
        super.broadcastChanges()
    }

    override fun clicked(slotIndex: Int, button: Int, actionType: ClickType, player: Player) {
        if (slotIndex in 0 until TEAM_JOIN_SIZE) {
            val teamKey = teamKeys.getOrNull(slotIndex)
            if (teamKey != null && player.uuid == viewerId) {
                service.requestJoin(service.playerFor(player), teamKey)
            }
            return
        }
    }

    override fun stillValid(player: Player): Boolean {
        return true
    }

    override fun slotsChanged(inventory: Container) {
    }

    override fun quickMoveStack(player: Player, slot: Int): ItemStack {
        return ItemStack.EMPTY
    }

    override fun moveItemStackTo(stack: ItemStack, startIndex: Int, endIndex: Int, reverseDirection: Boolean): Boolean {
        return false
    }
}

private class DisplaySlot(
    inventory: Container,
    index: Int,
    x: Int,
    y: Int,
) : Slot(inventory, index, x, y) {
    override fun mayPlace(stack: ItemStack): Boolean = false
    override fun mayPickup(player: Player): Boolean = false
}
