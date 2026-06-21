package me.jfenn.bingo.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {
    @Mutable @Accessor("isSaving")
    void setSaving(boolean saving);

    @Invoker("loadLevel")
    void invokeLoadLevel();
}
