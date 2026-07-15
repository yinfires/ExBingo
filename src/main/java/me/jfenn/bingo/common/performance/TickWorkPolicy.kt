package me.jfenn.bingo.common.performance

internal object TickWorkPolicy {
    const val ITEM_OBJECTIVE_SCAN_INTERVAL_TICKS = 3
    const val INVENTORY_MAINTENANCE_INTERVAL_TICKS = 10
    const val ELYTRA_REFRESH_INTERVAL_TICKS = 10

    fun shouldRun(ticks: Int, intervalTicks: Int, offsetTicks: Int = 0): Boolean {
        val interval = intervalTicks.coerceAtLeast(1)
        return Math.floorMod(ticks - offsetTicks, interval) == 0
    }

    fun shouldScanItemObjectives(cardTicks: Int): Boolean {
        return cardTicks <= 3 || shouldRun(cardTicks, ITEM_OBJECTIVE_SCAN_INTERVAL_TICKS)
    }

    fun shouldRunInventoryMaintenance(serverTicks: Int, offsetTicks: Int = 0): Boolean {
        return shouldRun(serverTicks, INVENTORY_MAINTENANCE_INTERVAL_TICKS, offsetTicks)
    }

    fun shouldRefreshElytra(serverTicks: Int): Boolean {
        return shouldRun(serverTicks, ELYTRA_REFRESH_INTERVAL_TICKS, offsetTicks = 2)
    }
}
