package me.jfenn.bingo.integrations.placeholders

import net.minecraft.server.MinecraftServer
import net.minecraft.network.chat.Component
import java.util.function.Function
import java.util.function.Supplier

interface ITextPlaceholdersApi {
    fun registerPlaceholder(id: String, handler: Function<IPlaceholderContext, Component>)
    fun parseText(message: Component, server: MinecraftServer): Component
    fun parseInline(message: Component, server: MinecraftServer, replacements: Map<String, Supplier<Component>>): Component
}

interface IPlaceholderContext {
    val server: MinecraftServer
}
