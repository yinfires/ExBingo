package me.jfenn.bingo.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Queue;

@Mixin(PersistentEntitySectionManager.class)
public interface PersistentEntitySectionManagerAccessor {
    @Invoker("updateChunkStatus")
    void invokeUpdateChunkStatus(ChunkPos chunkPos, FullChunkStatus fullChunkStatus);

    @Invoker("tick")
    void invokeTick();

    @Accessor("chunkLoadStatuses")
    Long2ObjectMap<?> getBingoChunkLoadStatuses();

    @Accessor("chunkVisibility")
    Long2ObjectMap<Visibility> getBingoChunkVisibility();

    @Accessor("loadingInbox")
    Queue<?> getBingoLoadingInbox();
}
