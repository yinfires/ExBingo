package me.jfenn.bingo.mixin;

import me.jfenn.bingo.common.ConstantsKt;
import me.jfenn.bingo.mixinhelper.ServerChunkManagerMixinHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MinecraftServerSkipStartRegionMixin {
    @Inject(at = @At(value = "HEAD"), method = "prepareLevels", cancellable = true)
    private void prepareStartRegion(ChunkProgressListener worldGenerationProgressListener, CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        if (
                server.isDedicatedServer()
                        || ServerChunkManagerMixinHelper.getShouldCancelSaving()
                        || server.getWorldData().getLevelName().startsWith(ConstantsKt.getBINGO_WORLD_PREFIX())
        ) {
            // The start region worldgen isn't all that useful in bingo, since the game starts at random coords anyway. So it doesn't really help.
            ci.cancel();
        }
    }
}
