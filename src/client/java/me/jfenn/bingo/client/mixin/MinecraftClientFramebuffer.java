package me.jfenn.bingo.client.mixin;

import me.jfenn.bingo.client.mixinhelper.FramebufferOverride;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.pipeline.RenderTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Minecraft.class, priority = 1001)
public class MinecraftClientFramebuffer {
    @Inject(
            at = @At(value = "HEAD"),
            method = "getMainRenderTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;",
            cancellable = true
    )
    void getMainRenderTarget(CallbackInfoReturnable<RenderTarget> ci) {
        RenderTarget framebuffer = FramebufferOverride.INSTANCE.getFramebuffer();
        if (framebuffer != null) {
            ci.setReturnValue(framebuffer);
            ci.cancel();
        }
    }
}
