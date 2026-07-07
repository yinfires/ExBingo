package me.jfenn.bingo.client.mixin;

import me.jfenn.bingo.client.mixinhandler.ItemDifficultyOverlayRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsItemDifficultyOverlayMixin {

    @Inject(
            method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            at = @At("HEAD")
    )
    private void exbingo$renderItemDifficulty(Font font, ItemStack stack, int x, int y, String text, CallbackInfo ci) {
        ItemDifficultyOverlayRenderer.INSTANCE.renderItemDecoration((GuiGraphics) (Object) this, font, stack, x, y);
    }

    @Inject(
            method = "renderFakeItem(Lnet/minecraft/world/item/ItemStack;III)V",
            at = @At("TAIL")
    )
    private void exbingo$renderAdvancementDifficulty(ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        ItemDifficultyOverlayRenderer.INSTANCE.renderAdvancementIcon((GuiGraphics) (Object) this, x, y);
    }
}
