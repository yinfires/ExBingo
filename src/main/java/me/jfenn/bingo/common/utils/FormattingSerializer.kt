package me.jfenn.bingo.common.utils

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.minecraft.ChatFormatting

object FormattingSerializer : KSerializer<ChatFormatting> {
    @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor("ChatFormatting", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ChatFormatting {
        val str = decoder.decodeString()
        return ChatFormatting.valueOf(str)
    }

    override fun serialize(encoder: Encoder, value: ChatFormatting) {
        encoder.encodeString(value.name)
    }
}
