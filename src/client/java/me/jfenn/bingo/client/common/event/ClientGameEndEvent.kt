package me.jfenn.bingo.client.common.event

import me.jfenn.bingo.platform.event.IEvent

/**
 * Fired client-side the moment a game ends (the POSTGAME end screen first opens),
 * while the player is still standing in the round's world — before they are
 * teleported back to the lobby. This is the only point at which the round's
 * dimension is the *current* one client-side, which Xaero's per-dimension map
 * reset APIs require. Used to wipe the round's explored map tiles and waypoints.
 */
object ClientGameEndEvent : IEvent<Unit>
