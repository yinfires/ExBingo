package me.jfenn.bingo.impl

import me.jfenn.bingo.platform.IMapService
import me.jfenn.bingo.platform.IMapState
import me.jfenn.bingo.platform.IServerWorld
import net.minecraft.world.level.material.MapColor
import net.minecraft.world.level.saveddata.maps.MapId
import net.minecraft.world.level.saveddata.maps.MapItemSavedData
import net.minecraft.server.MinecraftServer

class MapServiceImpl(
    private val server: MinecraftServer,
) : IMapService {
    override fun getMapColors(): Map<Byte, Int> {
        return  (4 until 248)
            .associate { i ->
                val mapColorRGB = MapColor.getColorFromPackedId(i)
                i.toUByte().toByte() to mapColorRGB
            }
    }

    override fun getNextMapId(): Int {
        return server.overworld().freeMapId.id
    }

    override fun createMapState(scale: Byte, locked: Boolean, world: IServerWorld): IMapState {
        require(world is ServerWorldImpl)
        val state = MapItemSavedData.createForClient(scale, locked, world.world.dimension())
        return MapStateImpl(state)
    }

    override fun putMapState(id: Int, state: IMapState) {
        require(state is MapStateImpl)
        server.overworld().setMapData(MapId(id), state.state)
    }
}

class MapStateImpl(
    val state: MapItemSavedData,
) : IMapState {
    override fun setColor(x: Int, z: Int, color: Byte) {
        state.setColor(x, z, color)
    }

    override fun copyFrom(source: ByteArray, destinationOffset: Int, startIndex: Int, endIndex: Int) {
        source.copyInto(state.colors, destinationOffset, startIndex, endIndex)
    }

    override fun markDirty(x: Int, z: Int) {
        state.accessor.invokeMarkDirty(x, z)
    }
}
