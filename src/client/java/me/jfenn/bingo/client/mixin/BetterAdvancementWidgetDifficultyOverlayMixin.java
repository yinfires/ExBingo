package me.jfenn.bingo.client.mixin;

import me.jfenn.bingo.client.mixinhandler.ItemDifficultyOverlayRenderer;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "betteradvancements.common.gui.BetterAdvancementWidget", remap = false)
public abstract class BetterAdvancementWidgetDifficultyOverlayMixin {

    @Shadow
    @Final
    private AdvancementNode advancementNode;

    @Shadow
    protected int x;

    @Shadow
    protected int y;

    @Inject(
            method = "draw",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;renderFakeItem(Lnet/minecraft/world/item/ItemStack;II)V",
                    shift = At.Shift.AFTER
            )
    )
    private void exbingo$renderAdvancementDifficulty(GuiGraphics guiGraphics, int scrollX, int scrollY, CallbackInfo ci) {
        renderDifficulty(guiGraphics, scrollX, scrollY);
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
            int scrollX,
            int scrollY,
            float fade,
            int left,
            int top,
            CallbackInfo ci
    ) {
        renderDifficulty(guiGraphics, scrollX, scrollY);
    }

    private void renderDifficulty(GuiGraphics guiGraphics, int scrollX, int scrollY) {
        ItemDifficultyOverlayRenderer.INSTANCE.renderAdvancementIcon(
                guiGraphics,
                this.advancementNode.holder().id().toString(),
                scrollX + this.x + 8,
                scrollY + this.y + 5
        );
    }
}
