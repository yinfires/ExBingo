package me.jfenn.bingo.common.timer

import me.jfenn.bingo.common.utils.milliseconds
import me.jfenn.bingo.common.utils.minus
import me.jfenn.bingo.common.utils.seconds
import java.time.Instant

internal object TerrainLoadingPolicy {
    val clientAckGrace = 500.milliseconds
    val recoveryTeleportDelay = 10.seconds
    val stillWaitingWarningDelay = 40.seconds

    fun hasLoadedTerrain(
        lastChunkBatch: Instant?,
        lastMovement: Instant?,
        loadingStarted: Instant,
        allowMovementFallback: Boolean,
    ): Boolean {
        val earliestAcceptedSignal = loadingStarted + clientAckGrace
        if (lastChunkBatch != null && lastChunkBatch > earliestAcceptedSignal) return true

        return allowMovementFallback &&
            lastMovement != null &&
            lastMovement > earliestAcceptedSignal
    }

    fun shouldSendRecoveryTeleport(
        now: Instant,
        startedLoading: Instant,
        lastRecoveryTeleport: Instant?,
    ): Boolean {
        if (now - startedLoading < recoveryTeleportDelay) return false
        return lastRecoveryTeleport == null || now - lastRecoveryTeleport >= recoveryTeleportDelay
    }

    fun shouldWarnStillWaiting(
        now: Instant,
        startedLoading: Instant,
        hasWarned: Boolean,
    ): Boolean {
        return !hasWarned && now - startedLoading > stillWaitingWarningDelay
    }
}
