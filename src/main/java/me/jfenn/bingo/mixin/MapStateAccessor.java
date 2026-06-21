package me.jfenn.bingo.mixin;

import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MapItemSavedData.class)
public interface MapStateAccessor {
    @Invoker("setColorsDirty")
    void invokeMarkDirty(int x, int z);
}
