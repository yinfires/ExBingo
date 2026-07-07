package me.jfenn.bingo.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.StringUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BookViewScreen.class)
public abstract class BookViewScreenSuggestCommandMixin {
    @Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
    private void exbingo$openChatForSuggestedCommand(Style style, CallbackInfoReturnable<Boolean> cir) {
        if (style == null || style.getClickEvent() == null) {
            return;
        }

        ClickEvent clickEvent = style.getClickEvent();
        if (clickEvent.getAction() == ClickEvent.Action.SUGGEST_COMMAND) {
            Minecraft.getInstance().setScreen(new ChatScreen(StringUtil.filterText(clickEvent.getValue())));
            cir.setReturnValue(true);
        }
    }
}
