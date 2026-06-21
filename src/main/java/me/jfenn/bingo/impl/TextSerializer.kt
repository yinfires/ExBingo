package me.jfenn.bingo.impl

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import me.jfenn.bingo.platform.ITextSerializer
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.chat.TextColor
import net.minecraft.ChatFormatting
import java.util.*

class TextSerializer : ITextSerializer {

    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    override fun toJson(text: Component): String {
        // this achieves Component.Serialization.toJsonString, but statically, without requiring RegistryWrapper
        val element = ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, text).getOrThrow()
        return gson.toJson(element)
    }

    override fun fromJson(json: String): Component {
        // this achieves Component.Serialization.fromJson, but statically, without requiring RegistryWrapper
        val element = JsonParser.parseString(json)
        return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, element).getOrThrow()
    }

    override fun toRawString(text: Component): String {
        val builder = StringBuilder()
        text.visit<Unit>(
            { style, asString ->
                if (style.isEmpty) builder.append(ChatFormatting.RESET)
                if (style.isBold) builder.append(ChatFormatting.BOLD)
                if (style.isItalic) builder.append(ChatFormatting.ITALIC)
                if (style.isStrikethrough) builder.append(ChatFormatting.STRIKETHROUGH)
                if (style.isUnderlined) builder.append(ChatFormatting.UNDERLINE)
                if (style.isObfuscated) builder.append(ChatFormatting.OBFUSCATED)

                ChatFormatting.entries
                    .filter { it.isColor }
                    .find {
                        val textColor = TextColor.fromLegacyFormat(it)
                        textColor != null && style.color?.value == textColor.value
                    }
                    ?.also { builder.append(it) }

                builder.append(asString)
                Optional.empty()
            },
            Style.EMPTY
        )
        return builder.toString()
    }

}
