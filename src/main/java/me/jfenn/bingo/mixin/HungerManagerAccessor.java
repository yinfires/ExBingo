package me.jfenn.bingo.mixin;

import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FoodData.class)
public interface HungerManagerAccessor {
    @Accessor("exhaustion")
    float getExhaustion();
    @Mutable @Accessor("exhaustion")
    void setExhaustion(float exhaustion);
}
