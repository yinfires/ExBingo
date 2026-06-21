package me.jfenn.bingo.integrations.placeholders

import net.minecraft.server.MinecraftServer
import net.minecraft.network.chat.Component
import java.util.function.Function
import java.util.function.Supplier

object DummyPlaceholders : ITextPlaceholdersApi {
    override fun registerPlaceholder(id: String, handler: Function<IPlaceholderContext, Component>) {}

    override fun parseText(message: Component, server: MinecraftServer): Component = message

    override fun parseInline(message: Component, server: MinecraftServer, replacements: Map<String, Supplier<Component>>): Component = message
}