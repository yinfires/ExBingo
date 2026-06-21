package me.jfenn.bingo.client.impl

import me.jfenn.bingo.ExBingoMod
import me.jfenn.bingo.client.platform.IResourcePackManager
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.repository.Pack
import net.minecraft.server.packs.repository.PackSource
import net.neoforged.neoforge.event.AddPackFindersEvent
import org.slf4j.Logger

internal class ResourcePackManager(
    private val log: Logger,
) : IResourcePackManager {
    private val packs = linkedSetOf<ResourceLocation>()

    init {
        ExBingoMod.getModEventBus()?.addListener(AddPackFindersEvent::class.java) { event ->
            if (event.packType != PackType.CLIENT_RESOURCES) return@addListener
            packs.forEach { id ->
                event.addPackFinders(
                    id,
                    PackType.CLIENT_RESOURCES,
                    Component.literal("ExBingo ${id.path}"),
                    PackSource.BUILT_IN,
                    false,
                    Pack.Position.TOP,
                )
            }
        }
    }

    override fun register(identifier: String) {
        val namespace = identifier.substringBefore(':')
        val path = identifier.substringAfter(':')
        val id = ResourceLocation.fromNamespaceAndPath(namespace, path)
        if (!packs.add(id)) log.debug("Resource pack {} is already registered", identifier)
    }
}
