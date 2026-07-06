package me.jfenn.bingo.common.scope

import me.jfenn.bingo.common.card.BingoCard
import me.jfenn.bingo.common.card.filter.ObjectiveFilterList
import me.jfenn.bingo.common.options.BingoCardOptions
import me.jfenn.bingo.common.options.BingoOptions
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class ScopeManagerPregameOptionsTest {
    @Test
    fun `pregame state reloads manually edited game options before regenerating cards`() {
        val savedOptions = BingoOptions(
            cards = listOf(
                BingoCardOptions(
                    itemDistribution = listOf(0, 0, 0, 9, 16),
                    itemFilter = ObjectiveFilterList.EVERYTHING,
                )
            )
        )
        val editedOptions = BingoOptions(
            cards = listOf(
                BingoCardOptions(
                    itemDistribution = listOf(5, 5, 5, 5, 5),
                    itemFilter = ObjectiveFilterList.fromString("+items -unobtainable"),
                )
            )
        )
        val state = BingoState(
            state = GameState.PREGAME,
            options = savedOptions,
        )
        state.cards += BingoCard.EMPTY

        state.copyPregameOptionsFrom(editedOptions)

        assertTrue(state.cards.isEmpty())
        assertEquals(editedOptions.cards.single().itemDistribution, state.options.cards.single().itemDistribution)
        assertEquals(editedOptions.cards.single().itemFilter, state.options.cards.single().itemFilter)
        assertNotSame(editedOptions.cards.single(), state.options.cards.single())
    }
}
