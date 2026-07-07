package me.jfenn.bingo.common.teamchest

import kotlinx.serialization.Serializable
import me.jfenn.bingo.platform.item.IItemStackSerialized

@Serializable
data class TeamChestData(
    val stacks: MutableList<IItemStackSerialized> = mutableListOf(),
)
