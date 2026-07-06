package me.jfenn.bingo.integrations.xaero

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XaeroWaypointShareMessageTest {
    @Test
    fun `detects current shared waypoint format`() {
        assertTrue(
            XaeroWaypointShareMessage.isShare(
                "xaero-waypoint:Home:H:10:64:-20:0:false:0:Internal-waypoints"
            )
        )
    }

    @Test
    fun `detects legacy shared waypoint format`() {
        assertTrue(
            XaeroWaypointShareMessage.isShare(
                "xaero_waypoint:Home:H:10:64:-20:0:false:0"
            )
        )
    }

    @Test
    fun `does not treat local waypoint add command as a share`() {
        assertFalse(
            XaeroWaypointShareMessage.isShare(
                "xaero_waypoint_add:Home:H:10:64:-20:0:false:0"
            )
        )
    }
}
