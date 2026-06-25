package me.jfenn.bingo.common.card.filter

sealed class ObjectiveFilter {
    abstract val tag: String
    data class Include(override val tag: String): ObjectiveFilter()
    data class Exclude(override val tag: String): ObjectiveFilter()
    data class Count(override val tag: String, val count: Int): ObjectiveFilter()

    companion object {
        const val UNCATEGORIZED = "uncategorized"
        const val UNOBTAINABLE = "unobtainable"
        // items whose placed block is unbreakable (bedrock, barrier, command blocks, ...)
        const val UNBREAKABLE = "unbreakable"
        const val TEDIOUS = "tedious"
        const val EVERYTHING = "everything"

        fun from(namespace: String) = "from=$namespace"
        fun type(type: String) = "type=$type"
    }
}
