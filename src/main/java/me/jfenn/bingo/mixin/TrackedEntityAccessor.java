package me.jfenn.bingo.mixin;

import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public interface TrackedEntityAccessor {

    @Accessor("entity")
    Entity exbingo$getEntity();

    @Accessor("serverEntity")
    ServerEntity exbingo$getServerEntity();

    @Accessor("seenBy")
    Set<ServerPlayerConnection> exbingo$getSeenBy();
}
