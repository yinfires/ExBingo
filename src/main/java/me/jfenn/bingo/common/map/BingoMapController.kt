package me.jfenn.bingo.common.map

import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.event.model.CardShuffledEvent
import me.jfenn.bingo.common.event.model.TeamWinnerEvent
import me.jfenn.bingo.common.event.packet.ServerPacketEvents
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.platform.IExecutors
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.model.ReloadEvent
import me.jfenn.bingo.platform.event.model.TickEvent
import me.jfenn.bingo.platform.item.IItemStackFactory
import net.minecraft.server.MinecraftServer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.UUID

/**
 * Handles re-rolling or generating new BINGO card layouts.
 */
internal class BingoMapController(
    events: ScopedEvents,
    eventBus: IEventBus,
    private val state: BingoState,
    private val playerManager: IPlayerManager,
    private val cardViewService: CardViewService,
    private val cardImageService: CardImageService,
    private val mapRenderService: MapRenderService,
    private val serverTaskExecutor: IExecutors.IServerTaskExecutor,
    private val packets: ServerPacketEvents,
    itemStackFactory: IItemStackFactory,
    server: MinecraftServer,
) : BingoComponent() {

    companion object {
        private val CARD_RESYNC_DELAYS_MS = listOf(250L, 1000L, 3000L)
    }

    private fun updateCards() {
        if (state.cards.isEmpty())
            return

        // update all card map images
        if (state.state == GameState.PREGAME) {
            // if in pregame, update the preview card!
            cardViewService.updateCard(null)
        }

        for (team in state.getRegisteredTeams()) {
            cardViewService.updateCard(team)
        }
    }

    private fun sendCardState(
        players: List<IPlayerHandle>,
        forceImages: Boolean = false,
    ) {
        if (forceImages) {
            players.forEach { cardImageService.clearPlayerState(it.uuid) }
        }

        cardImageService.sendNecessaryImages()
        players.forEach { cardViewService.sendUpdatePackets(it) }
    }

    private fun sendCardState(
        player: IPlayerHandle,
        forceImages: Boolean = false,
    ) {
        sendCardState(listOf(player), forceImages)
    }

    private fun schedulePlayerCardResync(
        playerId: UUID,
        forceImages: Boolean = false,
    ) {
        fun run() {
            playerManager.getPlayer(playerId)
                ?.let { sendCardState(it, forceImages) }
        }

        run()
        for (delay in CARD_RESYNC_DELAYS_MS) {
            CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS, serverTaskExecutor)
                .execute { run() }
        }
    }

    private fun scheduleAllCardResync(
        forceImages: Boolean = false,
    ) {
        fun run() {
            sendCardState(playerManager.getPlayers(), forceImages)
        }

        run()
        for (delay in CARD_RESYNC_DELAYS_MS) {
            CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS, serverTaskExecutor)
                .execute { run() }
        }
    }

    init {
        mapRenderService.validateItems(itemStackFactory.listItems(server))

        eventBus.register(TickEvent.Start) {
            // Only rebuild the card views every tick when a tile is actively flashing (achieved
            // within FLASHING_DURATION) — that is the sole state that needs per-tick map redraws.
            // Every other update (item captured/lost, card shuffled, team/option/state changes) is
            // driven by the discrete event handlers below, so the idle-game per-tick full rebuild
            // of all 25 tiles per team was pure wasted work. Behavior is unchanged: when nothing is
            // flashing, updateCard()'s tile diff is empty and it sends no packets anyway.
            if (state.state != GameState.PLAYING && state.state != GameState.PREGAME)
                return@register
            if (!cardViewService.hasFlashingTiles())
                return@register

            updateCards()
        }

        events.onPlayerJoin { (player) ->
            cardImageService.clearPlayerState(player.uuid)
        }

        events.onPlayerChannelRegister { (player) ->
            // Ensure any custom card images are available on the client before sending card views.
            // (Previously sendNecessaryImages() ran every tick; it only needs to run when the set
            // of card images could have changed or when a player (re)connects.)
            //
            // PlayerLoggedIn/ChannelRegister can still race with the client-side login/reset events,
            // so retry the full card state a few times and force image resends for this player.
            schedulePlayerCardResync(player.uuid, forceImages = true)
        }

        events.onPacket(packets.cardStateRequestV1) {
            // Let the client request a full HUD refresh once its login/reset path has settled.
            // This closes the race where the first server-pushed card display arrives but the
            // corresponding tile/image state is missed until a later menu/team update.
            sendCardState(it.player, forceImages = true)
        }

        events.onChangeTeam { event ->
            cardViewService.sendClearDisplayPacket(event.player)
            schedulePlayerCardResync(event.player.uuid)

            // Send updates to spectators whenever a player changes team
            playerManager.getPlayers()
                .filter { cardViewService.isViewingSpectatorCards(it) }
                .forEach { schedulePlayerCardResync(it.uuid) }
        }

        eventBus.register(TeamWinnerEvent) { event ->
            event.team.players
                .mapNotNull { playerManager.getPlayer(it.uuid) }
                .forEach { schedulePlayerCardResync(it.uuid) }
        }

        eventBus.register(CardShuffledEvent) {
            // A shuffled/replaced card may introduce new custom images; make sure clients have them.
            cardViewService.updateCard(it.cardId)
            cardImageService.sendNecessaryImages()
            cardViewService.sendCardShuffledPacket(it.cardId)
            scheduleAllCardResync()
        }

        events.onChangeOptions {
            updateCards()
            scheduleAllCardResync()
        }

        events.onStateChange { (_, to) ->
            val players = playerManager.getPlayers()

            if (to == GameState.PREGAME && state.isLobbyMode) {
                players.forEach { cardViewService.sendClearDisplayPacket(it) }
            }

            updateCards()
            scheduleAllCardResync()
        }

        eventBus.register(ReloadEvent.After) {
            cardImageService.clearPlayerStates()
            mapRenderService.clearCache()
            // Player image state was just cleared; re-send images for the current cards.
            scheduleAllCardResync(forceImages = true)
        }
    }
}
