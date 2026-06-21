package me.jfenn.bingo.impl.world

import me.jfenn.bingo.platform.world.IChunkHeightmap
import net.minecraft.world.level.levelgen.Heightmap

class ChunkHeightmapImpl(
    private val heightmap: Heightmap,
): IChunkHeightmap {
    override fun get(x: Int, z: Int): Int {
        return heightmap.getFirstAvailable(x, z)
    }
}
