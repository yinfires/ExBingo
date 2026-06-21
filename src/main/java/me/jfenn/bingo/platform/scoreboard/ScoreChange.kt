package me.jfenn.bingo.platform.scoreboard

import net.minecraft.network.chat.Component

sealed class ScoreChange {

    abstract val name: String
    abstract val text: Component?
    abstract val value: Int

    data class Create(
        override val name: String,
        override val text: Component,
        override val value: Int,
    ): ScoreChange()

    data class Update(
        override val name: String,
        override val text: Component,
        override val value: Int,
    ): ScoreChange()

    data class Remove(
        override val name: String,
        override val text: Component? = null,
        override val value: Int,
    ): ScoreChange()

}