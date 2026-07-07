package me.jfenn.bingo.client.mixin;

import me.jfenn.bingo.client.mixinhandler.ItemDifficultyOverlayRenderer;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AdvancementToast.class)
public abstract class AdvancementToastDifficultyOverlayMixin {

    @Shadow
    @Final
    private AdvancementHolder advancement;

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;renderFakeItem(Lnet/minecraft/world/item/ItemStack;II)V"
            )
    )
    private void exbingo$beginAdvancementDifficulty(
            GuiGraphics guiGraphics,
            ToastComponent toastComponent,
            long timeSinceLastVisible,
            CallbackInfoReturnable<Toast.Visibility> cir
    ) {
        ItemDifficultyOverlayRenderer.INSTANCE.beginAdvancementIcon(this.advancement.id().toString());
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;renderFakeItem(Lnet/minecraft/world/item/ItemStack;II)V",
                    shift = At.Shift.AFTER
            )
    )
    private void exbingo$endAdvancementDifficulty(
            GuiGraphics guiGraphics,
            ToastComponent toastComponent,
            long timeSinceLastVisible,
            CallbackInfoReturnable<Toast.Visibility> cir
    ) {
        ItemDifficultyOverlayRenderer.INSTANCE.endAdvancementIcon();
    }
}
