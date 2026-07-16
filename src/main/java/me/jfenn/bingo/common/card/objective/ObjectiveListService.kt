package me.jfenn.bingo.common.card.objective

import me.jfenn.bingo.common.utils.cacheFor
import me.jfenn.bingo.common.utils.seconds

class ObjectiveListService(
    private val objectiveManager: BingoObjectiveManager,
) {
    private val objectivesCache = cacheFor(5.seconds) { _: Unit -> objectiveManager.list() }
    private val cardSetObjectivesCache = cacheFor(5.seconds) { _: Unit ->
        (objectiveManager.list() + objectiveManager.listTyped())
            .distinct()
            .toList()
    }

    fun getAllObjectives(): Iterable<String> = objectivesCache.get(Unit)
    fun getCardSetObjectives(): Iterable<String> = cardSetObjectivesCache.get(Unit)
}
