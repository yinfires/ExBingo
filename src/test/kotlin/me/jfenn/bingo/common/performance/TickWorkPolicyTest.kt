package me.jfenn.bingo.common.performance

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TickWorkPolicyTest {
    @Test
    fun `item objectives scan eagerly at card start then every interval`() {
        for (tick in 0..3) {
            assertTrue(TickWorkPolicy.shouldScanItemObjectives(tick), "card tick $tick")
        }

        assertFalse(TickWorkPolicy.shouldScanItemObjectives(4))
        assertFalse(TickWorkPolicy.shouldScanItemObjectives(5))
        assertTrue(TickWorkPolicy.shouldScanItemObjectives(6))
        assertTrue(TickWorkPolicy.shouldScanItemObjectives(9))
    }

    @Test
    fun `inventory maintenance supports offsets`() {
        assertFalse(TickWorkPolicy.shouldRunInventoryMaintenance(10, offsetTicks = 1))
        assertTrue(TickWorkPolicy.shouldRunInventoryMaintenance(11, offsetTicks = 1))
        assertFalse(TickWorkPolicy.shouldRunInventoryMaintenance(12, offsetTicks = 1))
    }
}
