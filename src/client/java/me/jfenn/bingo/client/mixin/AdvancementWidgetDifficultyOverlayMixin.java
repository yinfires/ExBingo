package me.jfenn.bingo.client.mixin;

import me.jfenn.bingo.client.mixinhandler.ItemDifficultyOverlayRenderer;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AdvancementWidget.class)
public abstract class AdvancementWidgetDifficultyOverlayMixin {

    @Shadow
    @Final
    private AdvancementNode advancementNode;

    @Shadow
    @Final
    private int x;

    @Shadow
    @Final
    private int y;

    @Inject(
            method = "draw",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;renderFakeItem(Lnet/minecraft/world/item/ItemStack;II)V",
                    shift = At.Shift.AFTER
            )
    )
    private void exbingo$renderAdvancementDifficulty(GuiGraphics guiGraphics, int x, int y, CallbackInfo ci) {
        ItemDifficultyOverlayRenderer.INSTANCE.renderAdvancementIcon(
                guiGraphics,
                this.advancementNode.holder().id().toString(),
                x + this.x + 8,
                y + this.y + 5
        );
    }

    @Inject(
            method = "drawHover",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;renderFakeItem(Lnet/minecraft/world/item/ItemStack;II)V",
                    shift = At.Shift.AFTER
            )
    )
    private void exbingo$renderHoverAdvancementDifficulty(
            GuiGraphics guiGraphics,
            int x,
            int y,
            float fade,
            int width,
            int height,
            CallbackInfo ci
    ) {
        ItemDifficultyOverlayRenderer.INSTANCE.renderAdvancementIcon(
                guiGraphics,
                this.advancementNode.holder().id().toString(),
                x + this.x + 8,
                y + this.y + 5
        );
    }
}
