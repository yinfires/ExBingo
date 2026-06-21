package me.jfenn.bingo.impl

import me.jfenn.bingo.platform.EffectType
import me.jfenn.bingo.platform.IStatusEffectHandle
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects

class StatusEffectHandle(
    val instance: MobEffectInstance,
) : IStatusEffectHandle {
    override val type: EffectType = when (instance.effect) {
        MobEffects.NIGHT_VISION -> EffectType.NIGHT_VISION
        MobEffects.MOVEMENT_SLOWDOWN -> EffectType.SLOWNESS
        MobEffects.JUMP -> EffectType.JUMP_BOOST
        MobEffects.INVISIBILITY -> EffectType.INVISIBILITY
        else -> EffectType.OTHER
    }
    override val duration: Int = instance.duration
}
