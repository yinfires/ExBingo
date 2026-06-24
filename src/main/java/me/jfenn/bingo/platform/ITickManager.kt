package me.jfenn.bingo.platform

interface ITickManager {
    val isFrozen: Boolean
    val runsNormally: Boolean
    fun setFrozen(frozen: Boolean)
}
