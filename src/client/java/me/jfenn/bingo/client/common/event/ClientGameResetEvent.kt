package me.jfenn.bingo.client.common.event

import me.jfenn.bingo.platform.event.IEvent

/**
 * Fired client-side when a game ends and the player is returned to the lobby
 * (the POSTGAME end screen is cleared). Used to reset per-round client state such
 * as the Xaero map view.
 */
object ClientGameResetEvent : IEvent<Unit>
