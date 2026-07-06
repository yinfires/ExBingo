package me.jfenn.bingo.mixin;

import me.jfenn.bingo.mixinhandler.DedicatedServerPackConfigDefaults;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.world.level.DataPackConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DedicatedServerProperties.class)
public class DedicatedServerPropertiesMixin {
    @Inject(
            method = "getDatapackConfig(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/world/level/DataPackConfig;",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void enableBundleFeaturePackByDefault(String enabledPacks, String disabledPacks, CallbackInfoReturnable<DataPackConfig> cir) {
        cir.setReturnValue(DedicatedServerPackConfigDefaults.forceBundleFeaturePack(cir.getReturnValue()));
    }
}
