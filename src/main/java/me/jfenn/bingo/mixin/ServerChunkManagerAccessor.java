package me.jfenn.bingo.mixin;

import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerChunkCache.class)
public interface ServerChunkManagerAccessor {
    @Invoker("runDistanceManagerUpdates")
    boolean invokeUpdateChunks();
}
