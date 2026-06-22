package me.jfenn.bingo.mixin;

import me.jfenn.bingo.impl.PlayerHandle;
import me.jfenn.bingo.mixinhandler.PlayerAdvancementTrackerMixinHelper;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancements.class)
public class PlayerAdvancementTrackerPreventSpectatorsMixin {
    @Shadow
    private ServerPlayer player;

    @Inject(
            at = @At("HEAD"),
            method = "award",
            cancellable = true
    )
    public void award(AdvancementHolder advancement, String criterionName, CallbackInfoReturnable<Boolean> ci) {
        if (player == null) {
            return;
        }

        if (
                PlayerAdvancementTrackerMixinHelper.shouldPreventSpectatorAdvancements(
                        new PlayerHandle(player, null)
                )
        ) {
            ci.setReturnValue(false);
            ci.cancel();
        }
    }
}
