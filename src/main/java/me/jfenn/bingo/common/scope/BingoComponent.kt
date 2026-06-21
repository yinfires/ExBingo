package me.jfenn.bingo.common.scope

import org.slf4j.LoggerFactory

abstract class BingoComponent {
    init {
        LoggerFactory.getLogger("ExBingo")
            .takeIf { it.isDebugEnabled }
            ?.debug("Initialize [{}]", this::class.simpleName)
    }
}