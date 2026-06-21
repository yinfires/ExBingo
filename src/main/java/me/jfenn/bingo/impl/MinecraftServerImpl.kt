package me.jfenn.bingo.impl

import me.jfenn.bingo.platform.ILevelStorage
import me.jfenn.bingo.platform.IMinecraftServer
import me.jfenn.bingo.platform.ITickManager
import net.minecraft.server.MinecraftServer
import net.minecraft.resources.ResourceLocation
import net.minecraft.resources.ResourceKey
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.Level
import net.minecraft.world.level.dimension.DimensionType
import net.minecraft.world.level.storage.LevelResource
import java.nio.file.Path

class MinecraftServerImpl(
    override val server: MinecraftServer,
): ITickManager, ILevelStorage, IMinecraftServer {
    override val isSingleplayer: Boolean
        get() = server.isSingleplayer

    override val isDedicated: Boolean
        get() = server.isDedicatedServer

    override fun setFrozen(frozen: Boolean) {
        server.tickRateManager().setFrozen(frozen)
    }

    override fun getLevelSaveDir(worldId: ResourceLocation): Path? {
        val world = server.allLevels.find { it.dimension().location() == worldId }
            ?: return null

        val root = server.getWorldPath(LevelResource.ROOT)
        val dimensionKey: ResourceKey<Level> = ResourceKey.create(Registries.DIMENSION, worldId)
        return DimensionType.getStorageFolder(dimensionKey, root)
    }
}
