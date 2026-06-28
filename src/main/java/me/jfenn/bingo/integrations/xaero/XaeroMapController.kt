package me.jfenn.bingo.integrations.xaero

import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.game.GameResetEvent

/**
 * On returning to the lobby after a game ([GameResetEvent]), switch every online
 * player's Xaero map to a fresh world id so the finished round's explored tiles
 * and waypoints stop showing. By this point the reset has already teleported
 * players back to the lobby dimension, so Xaero has settled on the lobby and our
 * fresh id wins.
 */
class XaeroMapController(
    eventBus: IEventBus,
    private val playerManager: IPlayerManager,
    private val xaeroMap: IXaeroMapApi,
) : BingoComponent() {

    init {
        eventBus.register(GameResetEvent) {
            if (xaeroMap.isInstalled()) {
                xaeroMap.switchToFreshMapWorld(playerManager.getPlayers().map { it.player })
            }
        }
    }
}
