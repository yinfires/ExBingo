package me.jfenn.bingo.mixin;

import me.jfenn.bingo.mixinhandler.ExperienceBottleXpHelper;
import net.minecraft.world.entity.projectile.ThrownExperienceBottle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ThrownExperienceBottle.class)
public abstract class ThrownExperienceBottleMixin {
    @ModifyArg(
            method = "onHit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V"
            ),
            index = 2
    )
    private int exbingo$modifyXpAmount(int original) {
        var random = ((ThrownExperienceBottle) (Object) this).level().random;
        return ExperienceBottleXpHelper.modifyAmount(random, original);
    }
}
