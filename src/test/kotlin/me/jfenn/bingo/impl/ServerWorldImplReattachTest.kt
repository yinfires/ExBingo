package me.jfenn.bingo.impl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerWorldImplReattachTest {
    @Test
    fun `NeoForge reattach sequence moves chunk source after addDuringTeleport and before client sync`() {
        val calls = mutableListOf<String>()

        runNeoForgePlayerReattachSequence(object : RecordingReattachOps(calls) {})

        assertEquals(
            listOf(
                "addPostTeleportTicket",
                "runDistanceManagerUpdates",
                "stopRiding",
                "sendRespawnPacket",
                "sendDifficulty",
                "sendPermission",
                "removeFromOldLevel",
                "revive",
                "setServerLevel",
                "teleportConnection",
                "resetConnectionPosition",
                "addDuringTeleport",
                "updatePlayerStatus",
                "moveChunkSource",
                "runDistanceManagerUpdates",
                "sendAbilities",
                "sendLevelInfo",
                "sendAllPlayerInfo",
                "sendActiveEffects",
                "syncAttachments",
                "markDimensionChanged",
                "fireChangedDimensionEvent",
            ),
            calls,
        )
    }

    @Test
    fun `player chunk tracking helper refreshes distance manager before and after chunk map registration`() {
        val calls = mutableListOf<String>()
        val ops = RecordingChunkTrackingOps(calls)

        preparePlayerChunkTracking(ops)
        assertEquals(listOf("addPostTeleportTicket", "runDistanceManagerUpdates"), calls)

        finishPlayerChunkTracking(ops)
        assertEquals(
            listOf(
                "addPostTeleportTicket",
                "runDistanceManagerUpdates",
                "updatePlayerStatus",
                "moveChunkSource",
                "runDistanceManagerUpdates",
            ),
            calls,
        )
    }

    @Test
    fun `player attachment diagnostics are healthy only when player is fully tracked by new level`() {
        assertTrue(
            isPlayerLevelAttachmentHealthy(
                PlayerLevelAttachmentDiagnostics(
                    attached = true,
                    inLevelPlayers = true,
                    entityTracked = true,
                    entitiesLoaded = true,
                    positionEntityTicking = true,
                    blockTicking = true,
                    distanceEntityTicking = true,
                    distanceBlockTicking = true,
                    chunkMapWatching = true,
                ),
            ),
        )
    }

    @Test
    fun `player attachment diagnostics fail when entity manager chunk lifecycle is not loaded and ticking`() {
        assertFalse(
            isPlayerLevelAttachmentHealthy(
                PlayerLevelAttachmentDiagnostics(
                    attached = true,
                    inLevelPlayers = true,
                    entityTracked = true,
                    entitiesLoaded = false,
                    positionEntityTicking = true,
                    blockTicking = true,
                    distanceEntityTicking = true,
                    distanceBlockTicking = true,
                    chunkMapWatching = true,
                    entityLoadStatus = ENTITY_CHUNK_LOAD_STATUS_FRESH,
                    entityVisibility = ENTITY_CHUNK_VISIBILITY_TICKING,
                ),
            ),
        )
        assertFalse(
            isPlayerLevelAttachmentHealthy(
                PlayerLevelAttachmentDiagnostics(
                    attached = true,
                    inLevelPlayers = true,
                    entityTracked = true,
                    entitiesLoaded = true,
                    positionEntityTicking = false,
                    blockTicking = true,
                    distanceEntityTicking = true,
                    distanceBlockTicking = true,
                    chunkMapWatching = true,
                    entityLoadStatus = ENTITY_CHUNK_LOAD_STATUS_LOADED,
                    entityVisibility = "TRACKED",
                ),
            ),
        )
    }

    @Test
    fun `entity chunk lifecycle is healthy only when loaded and ticking`() {
        assertTrue(isEntityChunkLifecycleHealthy(ENTITY_CHUNK_LOAD_STATUS_LOADED, ENTITY_CHUNK_VISIBILITY_TICKING))
        assertFalse(isEntityChunkLifecycleHealthy(ENTITY_CHUNK_LOAD_STATUS_FRESH, ENTITY_CHUNK_VISIBILITY_TICKING))
        assertFalse(isEntityChunkLifecycleHealthy(ENTITY_CHUNK_LOAD_STATUS_LOADED, "TRACKED"))
    }

    @Test
    fun `player attachment diagnostics fail when serverLevel points at new level but players list does not contain player`() {
        assertFalse(
            isPlayerLevelAttachmentHealthy(
                PlayerLevelAttachmentDiagnostics(
                    attached = true,
                    inLevelPlayers = false,
                    entityTracked = true,
                    entitiesLoaded = true,
                    positionEntityTicking = true,
                    blockTicking = true,
                    distanceEntityTicking = true,
                    distanceBlockTicking = true,
                    chunkMapWatching = true,
                ),
            ),
        )
    }

    @Test
    fun `player attachment diagnostics fail when chunk or entity tracking is not ticking`() {
        assertFalse(
            isPlayerLevelAttachmentHealthy(
                PlayerLevelAttachmentDiagnostics(
                    attached = true,
                    inLevelPlayers = true,
                    entityTracked = false,
                    entitiesLoaded = true,
                    positionEntityTicking = true,
                    blockTicking = true,
                    distanceEntityTicking = true,
                    distanceBlockTicking = true,
                    chunkMapWatching = true,
                ),
            ),
        )
        assertFalse(
            isPlayerLevelAttachmentHealthy(
                PlayerLevelAttachmentDiagnostics(
                    attached = true,
                    inLevelPlayers = true,
                    entityTracked = true,
                    entitiesLoaded = true,
                    positionEntityTicking = false,
                    blockTicking = true,
                    distanceEntityTicking = true,
                    distanceBlockTicking = true,
                    chunkMapWatching = true,
                ),
            ),
        )
        assertFalse(
            isPlayerLevelAttachmentHealthy(
                PlayerLevelAttachmentDiagnostics(
                    attached = true,
                    inLevelPlayers = true,
                    entityTracked = true,
                    entitiesLoaded = true,
                    positionEntityTicking = true,
                    blockTicking = false,
                    distanceEntityTicking = true,
                    distanceBlockTicking = true,
                    chunkMapWatching = true,
                ),
            ),
        )
    }

    @Test
    fun `player attachment diagnostics fail when distance manager or chunk map does not track player`() {
        assertFalse(
            isPlayerLevelAttachmentHealthy(
                PlayerLevelAttachmentDiagnostics(
                    attached = true,
                    inLevelPlayers = true,
                    entityTracked = true,
                    entitiesLoaded = true,
                    positionEntityTicking = true,
                    blockTicking = true,
                    distanceEntityTicking = false,
                    distanceBlockTicking = true,
                    chunkMapWatching = true,
                ),
            ),
        )
        assertFalse(
            isPlayerLevelAttachmentHealthy(
                PlayerLevelAttachmentDiagnostics(
                    attached = true,
                    inLevelPlayers = true,
                    entityTracked = true,
                    entitiesLoaded = true,
                    positionEntityTicking = true,
                    blockTicking = true,
                    distanceEntityTicking = true,
                    distanceBlockTicking = false,
                    chunkMapWatching = true,
                ),
            ),
        )
        assertFalse(
            isPlayerLevelAttachmentHealthy(
                PlayerLevelAttachmentDiagnostics(
                    attached = true,
                    inLevelPlayers = true,
                    entityTracked = true,
                    entitiesLoaded = true,
                    positionEntityTicking = true,
                    blockTicking = true,
                    distanceEntityTicking = true,
                    distanceBlockTicking = true,
                    chunkMapWatching = false,
                ),
            ),
        )
    }

    private class RecordingChunkTrackingOps(
        private val calls: MutableList<String>,
    ) : PlayerChunkTrackingOps {
        override fun addPostTeleportTicket() {
            calls += "addPostTeleportTicket"
        }

        override fun runDistanceManagerUpdates() {
            calls += "runDistanceManagerUpdates"
        }

        override fun updatePlayerStatus() {
            calls += "updatePlayerStatus"
        }

        override fun moveChunkSource() {
            calls += "moveChunkSource"
        }
    }

    private abstract class RecordingReattachOps(
        private val calls: MutableList<String>,
    ) : NeoForgePlayerReattachOps {
        override fun addPostTeleportTicket() {
            calls += "addPostTeleportTicket"
        }

        override fun runDistanceManagerUpdates() {
            calls += "runDistanceManagerUpdates"
        }

        override fun stopRiding() {
            calls += "stopRiding"
        }

        override fun sendRespawnPacket() {
            calls += "sendRespawnPacket"
        }

        override fun sendDifficulty() {
            calls += "sendDifficulty"
        }

        override fun sendPermission() {
            calls += "sendPermission"
        }

        override fun removeFromOldLevel() {
            calls += "removeFromOldLevel"
        }

        override fun revive() {
            calls += "revive"
        }

        override fun setServerLevel() {
            calls += "setServerLevel"
        }

        override fun teleportConnection() {
            calls += "teleportConnection"
        }

        override fun resetConnectionPosition() {
            calls += "resetConnectionPosition"
        }

        override fun addDuringTeleport() {
            calls += "addDuringTeleport"
        }

        override fun updatePlayerStatus() {
            calls += "updatePlayerStatus"
        }

        override fun moveChunkSource() {
            calls += "moveChunkSource"
        }

        override fun sendAbilities() {
            calls += "sendAbilities"
        }

        override fun sendLevelInfo() {
            calls += "sendLevelInfo"
        }

        override fun sendAllPlayerInfo() {
            calls += "sendAllPlayerInfo"
        }

        override fun sendActiveEffects() {
            calls += "sendActiveEffects"
        }

        override fun syncAttachments() {
            calls += "syncAttachments"
        }

        override fun markDimensionChanged() {
            calls += "markDimensionChanged"
        }

        override fun fireChangedDimensionEvent() {
            calls += "fireChangedDimensionEvent"
        }
    }
}
