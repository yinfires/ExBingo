package me.jfenn.bingo.client.platform

import me.jfenn.bingo.platform.text.IText

interface IKeyBindingManager {
    fun registerKey(
        translationKey: String,
        code: Int,
        category: String,
    ): IKeyBinding
}

interface IKeyBinding {
    val displayName: IText
    fun isPressed(): Boolean
    fun wasPressed(): Boolean
}
