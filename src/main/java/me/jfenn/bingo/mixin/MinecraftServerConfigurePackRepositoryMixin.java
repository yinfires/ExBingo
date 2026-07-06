package me.jfenn.bingo.mixin;

import me.jfenn.bingo.mixinhandler.DedicatedServerPackConfigDefaults;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.WorldDataConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
public class MinecraftServerConfigurePackRepositoryMixin {
    @Inject(
            method = "configurePackRepository",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void forceBundleFeaturePack(
            PackRepository packRepository,
            WorldDataConfiguration initialDataConfig,
            boolean initMode,
            boolean safeMode,
            CallbackInfoReturnable<WorldDataConfiguration> cir
    ) {
        cir.setReturnValue(DedicatedServerPackConfigDefaults.forceBundleFeaturePack(packRepository, cir.getReturnValue()));
    }
}
