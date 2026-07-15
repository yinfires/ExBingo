package me.jfenn.bingo.common.state

import me.jfenn.bingo.common.map.BingoMap
import me.jfenn.bingo.common.menu.MenuPage
import me.jfenn.bingo.common.options.BingoOptions
import me.jfenn.bingo.common.options.RestoreTimeLimit
import me.jfenn.bingo.common.scoring.GameMessage
import me.jfenn.bingo.common.spawn.PlayerState
import me.jfenn.bingo.common.team.BingoTeamKey
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BingoStateTest {
    @Test
    fun `reset clears previous game runtime state and creates a new game id`() {
        val options = BingoOptions(
            cards = emptyList(),
        )
        val state = BingoState(
            isLobbyMode = true,
            state = GameState.PLAYING,
            startedAt = Instant.parse("2026-06-24T00:00:00Z"),
            updatedAt = Instant.parse("2026-06-24T00:05:00Z"),
            endedAt = Instant.parse("2026-06-24T00:06:00Z"),
            timeOffline = Duration.ofSeconds(30),
            timeAdjustment = Duration.ofSeconds(5),
            options = options,
        )
        val oldGameId = state.gameId
        val playerId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val spectatorId = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val teamKey = BingoTeamKey("bingo_red")

        state.restoreOptions += RestoreTimeLimit(Duration.ofMinutes(2))
        state.playersJoinedIds += playerId
        state.players[playerId] = PlayerState(lastGameId = oldGameId, lastState = GameState.PLAYING)
        state.playersSpectatingIds += spectatorId
        state.previewMap = BingoMap(99)
        state.gameMessages += GameMessage.CardCompleted(Duration.ofSeconds(1), teamKey, isAutoWin = false)
        state.isForfeit = true
        state.menu.page = MenuPage.FEATURES

        state.reset()

        assertNotEquals(oldGameId, state.gameId)
        assertNull(state.startedAt)
        assertNull(state.updatedAt)
        assertNull(state.endedAt)
        assertEquals(Duration.ZERO, state.timeOffline)
        assertEquals(Duration.ZERO, state.timeAdjustment)
        assertTrue(state.teams.isEmpty())
        assertTrue(state.playersJoinedIds.isEmpty())
        assertTrue(state.players.isEmpty())
        assertTrue(state.playersSpectatingIds.isEmpty())
        assertNull(state.previewMap)
        assertTrue(state.cards.isEmpty())
        assertTrue(state.gameMessages.isEmpty())
        assertEquals(false, state.isForfeit)
        assertEquals(MenuPage.ROOT, state.menu.page)
        assertTrue(state.restoreOptions.isEmpty())
        assertEquals(Duration.ofMinutes(2), state.options.timeLimit)
    }

    @Test
    fun `active game includes startup loading countdown and playing states`() {
        val activeStates = setOf(
            GameState.STARTING,
            GameState.PRELOADING,
            GameState.LOADING,
            GameState.COUNTDOWN,
            GameState.PLAYING,
        )

        for (state in GameState.entries) {
            assertEquals(state in activeStates, state.isActiveGame, "active game policy for $state")
        }
    }
}
