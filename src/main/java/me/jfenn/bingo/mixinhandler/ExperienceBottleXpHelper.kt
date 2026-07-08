package me.jfenn.bingo.mixinhandler

import me.jfenn.bingo.common.config.BingoConfig
import net.minecraft.util.RandomSource
import kotlin.math.max
import kotlin.math.min

object ExperienceBottleXpHelper {
    private const val MIN_CONFIG_XP = 0
    private const val MAX_CONFIG_XP = 100000

    @JvmField
    var enabled: Boolean = true

    @JvmField
    var minXp: Int = 100

    @JvmField
    var maxXp: Int = 500

    @JvmStatic
    fun updateFrom(config: BingoConfig) {
        enabled = config.experienceBottleXp.enabled
        minXp = config.experienceBottleXp.min
        maxXp = config.experienceBottleXp.max
    }

    @JvmStatic
    fun modifyAmount(random: RandomSource, vanillaAmount: Int): Int {
        if (!enabled) {
            return vanillaAmount
        }

        val lower = min(minXp, maxXp).coerceIn(MIN_CONFIG_XP, MAX_CONFIG_XP)
        val upper = max(minXp, maxXp).coerceIn(MIN_CONFIG_XP, MAX_CONFIG_XP)
        return lower + random.nextInt(upper - lower + 1)
    }
}
