package me.jfenn.bingo.impl

import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IStatHandle
import me.jfenn.bingo.platform.IStatManager
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.stats.Stat
import net.minecraft.stats.StatsCounter
import net.minecraft.stats.StatType
import net.minecraft.resources.ResourceLocation

class StatManagerImpl : IStatManager {

    override fun list(): List<IStatHandle> {
        return BuiltInRegistries.STAT_TYPE
            .flatten()
            .map { StatHandleImpl(it) }
    }

    override fun getById(type: String, name: String?): IStatHandle? {
        val typeId = ResourceLocation.tryParse(type) ?: return null
        val statType = BuiltInRegistries.STAT_TYPE.get(typeId) ?: return null
        @Suppress("UNCHECKED_CAST") (statType as StatType<Any>)

        if (name != null) {
            val nameId = ResourceLocation.tryParse(name) ?: return null
            val statName = statType.registry.get(nameId) ?: return null

            return StatHandleImpl(
                stat = statType.get(statName),
            )
        } else {
            return SummedStatHandleImpl(statType)
        }
    }

    inner class StatHandleImpl<T>(
        private val stat: Stat<T>,
        ) : IStatHandle {
        override fun getForPlayer(player: IPlayerHandle): Int {
            require(player is PlayerHandle)
            val statHandler: StatsCounter = player.player.stats
            return statHandler.getValue(stat)
        }

        override fun reset(player: IPlayerHandle) {
            require(player is PlayerHandle)
            player.player.resetStat(stat)
        }
    }

    inner class SummedStatHandleImpl<T>(
        private val type: StatType<T>,
        ) : IStatHandle {
        override fun getForPlayer(player: IPlayerHandle): Int {
            require(player is PlayerHandle)
            val statHandler: StatsCounter = player.player.stats
            return type.sumOf { statHandler.getValue(it) }
        }

        override fun reset(player: IPlayerHandle) {
            require(player is PlayerHandle)
            type.forEach { player.player.resetStat(it) }
        }
    }
}
