package me.jfenn.bingo.client.impl.screen

import me.jfenn.bingo.client.platform.screen.IKeyInput

class KeyInputImpl(
    override val keyCode: Int,
    override val scanCode: Int,
): IKeyInput {
    override val isEscape: Boolean
        get() = keyCode == 256
}
