package me.jfenn.bingo.integrations.xaero

internal object XaeroWaypointShareMessage {
    private const val WAYPOINT_SHARE_PREFIX = "xaero-waypoint:"
    private const val WAYPOINT_OLD_SHARE_PREFIX = "xaero_waypoint:"

    fun isShare(rawMessage: String): Boolean =
        rawMessage.contains(WAYPOINT_SHARE_PREFIX) || rawMessage.contains(WAYPOINT_OLD_SHARE_PREFIX)
}
