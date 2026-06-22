package me.jfenn.bingo.mixin;

import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PrimaryLevelData.class)
public interface LevelPropertiesAccessor {
    @Accessor("worldOptions")
    WorldOptions getWorldOptions();
    @Mutable @Accessor("worldOptions")
    void setWorldOptions(WorldOptions worldOptions);
}
