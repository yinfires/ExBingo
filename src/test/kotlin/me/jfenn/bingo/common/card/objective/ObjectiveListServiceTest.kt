package me.jfenn.bingo.common.card.objective

import me.jfenn.bingo.common.card.BingoCard
import me.jfenn.bingo.common.card.CardGeneratorState
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.impl.TextFactoryImpl
import org.slf4j.helpers.NOPLogger
import kotlin.test.Test
import kotlin.test.assertEquals

class ObjectiveListServiceTest {
    @Test
    fun `card set suggestions include typed item and advancement ids`() {
        val objectiveManager = BingoObjectiveManager(
            providers = listOf(
                FakeObjectiveManager(
                    ids = listOf("example:item"),
                    typedIds = listOf("item!example:item"),
                ),
                FakeObjectiveManager(
                    ids = listOf("example:advancement"),
                    typedIds = listOf("advancement!example:advancement"),
                ),
            ),
            text = TextProvider(NOPLogger.NOP_LOGGER, TextFactoryImpl()),
            log = NOPLogger.NOP_LOGGER,
        )
        val service = ObjectiveListService(objectiveManager)

        assertEquals(
            listOf(
                "example:item",
                "example:advancement",
                "item!example:item",
                "advancement!example:advancement",
            ),
            service.getCardSetObjectives().toList()
        )
    }

    private class FakeObjectiveManager(
        private val ids: Iterable<String>,
        private val typedIds: Iterable<String>,
    ) : IObjectiveManager {
        override fun list(): Iterable<String> = ids
        override fun listTyped(): Iterable<String> = typedIds
        override fun find(id: String, state: CardGeneratorState): BingoObjective? = null
        override fun init(card: BingoCard) = Unit
        override fun tick(card: BingoCard) = Unit
    }
}
