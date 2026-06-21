package me.jfenn.bingo.mixin;

import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PrimaryLevelData.class)
public interface LevelPropertiesAccessor {
    @Accessor("generatorOptions")
    WorldOptions getGeneratorOptions();
    @Mutable @Accessor("generatorOptions")
    void setGeneratorOptions(WorldOptions generatorOptions);
    @Mutable @Accessor("dragonFight")
    void setDragonFight(EndDragonFight.Data dragonFight);
}
