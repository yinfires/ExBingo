package me.jfenn.bingo.platform

import net.minecraft.resources.ResourceLocation
import java.nio.file.Path

interface ILevelStorage {
    fun getLevelSaveDir(worldId: ResourceLocation): Path?
}