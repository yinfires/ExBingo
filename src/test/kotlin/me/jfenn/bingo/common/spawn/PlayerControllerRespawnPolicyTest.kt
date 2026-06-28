package me.jfenn.bingo.common.spawn

import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.platform.utils.UuidAsString
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerControllerRespawnPolicyTest {
    private val gameId: UuidAsString = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val otherGameId: UuidAsString = UUID.fromString("00000000-0000-0000-0000-000000000102")

    @Test
    fun `playing after loading in the same game does not force redundant respawn`() {
        val oldState = PlayerState(
            lastGameId = gameId,
            lastState = GameState.LOADING,
        )
        val newState = PlayerState(
            lastGameId = gameId,
            lastState = GameState.PLAYING,
        )

        assertFalse(
            shouldRespawnPlayer(
                oldPlayerState = oldState,
                newPlayerState = newState,
                state = GameState.PLAYING,
                forceReset = true,
                isOnActiveTeam = true,
            ),
        )
    }

    @Test
    fun `playing with a different game id still respawns`() {
        val oldState = PlayerState(
            lastGameId = otherGameId,
            lastState = GameState.LOADING,
        )
        val newState = PlayerState(
            lastGameId = gameId,
            lastState = GameState.PLAYING,
        )

        assertTrue(
            shouldRespawnPlayer(
                oldPlayerState = oldState,
                newPlayerState = newState,
                state = GameState.PLAYING,
                forceReset = true,
                isOnActiveTeam = true,
            ),
        )
    }

    @Test
    fun `countdown to playing in the same game does not force redundant respawn`() {
        val oldState = PlayerState(
            lastGameId = gameId,
            lastState = GameState.COUNTDOWN,
        )
        val newState = PlayerState(
            lastGameId = gameId,
            lastState = GameState.PLAYING,
        )

        assertFalse(
            shouldRespawnPlayer(
                oldPlayerState = oldState,
                newPlayerState = newState,
                state = GameState.PLAYING,
                forceReset = true,
                isOnActiveTeam = true,
            ),
        )
    }

    @Test
    fun `resuming the same game from postgame does not respawn and wipe inventory`() {
        val oldState = PlayerState(
            lastGameId = gameId,
            lastState = GameState.POSTGAME,
        )
        val newState = PlayerState(
            lastGameId = gameId,
            lastState = GameState.PLAYING,
        )

        assertFalse(
            shouldRespawnPlayer(
                oldPlayerState = oldState,
                newPlayerState = newState,
                state = GameState.PLAYING,
                forceReset = false,
                isOnActiveTeam = true,
            ),
        )
    }
}
