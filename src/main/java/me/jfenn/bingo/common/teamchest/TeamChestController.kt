package me.jfenn.bingo.common.teamchest

import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.state.GameState

internal class TeamChestController(
    events: ScopedEvents,
    private val teamChestService: TeamChestService,
) {
    init {
        events.onEnter(GameState.PREGAME, requireChange = true) {
            teamChestService.clearAll()
        }
    }
}
