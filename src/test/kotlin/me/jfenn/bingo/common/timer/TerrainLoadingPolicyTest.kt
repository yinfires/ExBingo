package me.jfenn.bingo.common.timer

import me.jfenn.bingo.common.utils.seconds
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerrainLoadingPolicyTest {
    private val started = Instant.parse("2026-07-07T00:00:00Z")

    @Test
    fun `chunk batch ack must arrive after loading grace`() {
        assertFalse(
            TerrainLoadingPolicy.hasLoadedTerrain(
                lastChunkBatch = started + TerrainLoadingPolicy.clientAckGrace,
                lastMovement = null,
                loadingStarted = started,
                allowMovementFallback = false,
            )
        )

        assertTrue(
            TerrainLoadingPolicy.hasLoadedTerrain(
                lastChunkBatch = started + TerrainLoadingPolicy.clientAckGrace + 1.seconds,
                lastMovement = null,
                loadingStarted = started,
                allowMovementFallback = false,
            )
        )
    }

    @Test
    fun `network players cannot satisfy terrain loading with movement only`() {
        assertFalse(
            TerrainLoadingPolicy.hasLoadedTerrain(
                lastChunkBatch = null,
                lastMovement = started + TerrainLoadingPolicy.clientAckGrace + 1.seconds,
                loadingStarted = started,
                allowMovementFallback = false,
            )
        )
    }

    @Test
    fun `integrated server memory connections may use movement fallback`() {
        assertTrue(
            TerrainLoadingPolicy.hasLoadedTerrain(
                lastChunkBatch = null,
                lastMovement = started + TerrainLoadingPolicy.clientAckGrace + 1.seconds,
                loadingStarted = started,
                allowMovementFallback = true,
            )
        )
    }

    @Test
    fun `recovery teleport waits for the configured delay`() {
        assertFalse(
            TerrainLoadingPolicy.shouldSendRecoveryTeleport(
                now = started + TerrainLoadingPolicy.recoveryTeleportDelay - 1.seconds,
                startedLoading = started,
                lastRecoveryTeleport = null,
            )
        )

        assertTrue(
            TerrainLoadingPolicy.shouldSendRecoveryTeleport(
                now = started + TerrainLoadingPolicy.recoveryTeleportDelay,
                startedLoading = started,
                lastRecoveryTeleport = null,
            )
        )
    }
}
