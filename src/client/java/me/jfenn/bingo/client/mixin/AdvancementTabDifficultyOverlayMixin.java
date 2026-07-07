package me.jfenn.bingo.client.mixin;

import me.jfenn.bingo.client.mixinhandler.ItemDifficultyOverlayRenderer;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AdvancementTab.class)
public abstract class AdvancementTabDifficultyOverlayMixin {

    @Shadow
    @Final
    private AdvancementNode rootNode;

    @Inject(
            method = "drawIcon",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/advancements/AdvancementTabType;drawIcon(Lnet/minecraft/client/gui/GuiGraphics;IIILnet/minecraft/world/item/ItemStack;)V"
            )
    )
    private void exbingo$beginAdvancementDifficulty(GuiGraphics guiGraphics, int offsetX, int offsetY, CallbackInfo ci) {
        ItemDifficultyOverlayRenderer.INSTANCE.beginAdvancementIcon(this.rootNode.holder().id().toString());
    }

    @Inject(
            method = "drawIcon",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/advancements/AdvancementTabType;drawIcon(Lnet/minecraft/client/gui/GuiGraphics;IIILnet/minecraft/world/item/ItemStack;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void exbingo$endAdvancementDifficulty(GuiGraphics guiGraphics, int offsetX, int offsetY, CallbackInfo ci) {
        ItemDifficultyOverlayRenderer.INSTANCE.endAdvancementIcon();
    }
}
