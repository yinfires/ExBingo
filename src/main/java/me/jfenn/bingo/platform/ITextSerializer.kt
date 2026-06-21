package me.jfenn.bingo.platform

import net.minecraft.network.chat.Component

interface ITextSerializer {
    fun toJson(text: Component): String
    fun fromJson(json: String): Component
    fun toRawString(text: Component): String
}