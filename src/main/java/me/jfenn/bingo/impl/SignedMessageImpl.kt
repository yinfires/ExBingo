package me.jfenn.bingo.impl

import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.commands.ISignedMessage
import net.minecraft.network.chat.PlayerChatMessage
import java.util.*

class SignedMessageImpl(
    val message: PlayerChatMessage,
) : ISignedMessage {
    override val sender: UUID
        get() = message.link().sender()
    override val text: IText
        get() = TextImpl(message.decoratedContent().copy())
    override val raw: String
        get() = message.signedContent()

    override fun withUnsignedContent(text: IText): ISignedMessage {
        return SignedMessageImpl(message.withUnsignedContent(text.value))
    }
}
