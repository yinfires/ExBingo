package me.jfenn.bingo.mixin;

import me.jfenn.bingo.mixinhandler.RevealAllAdvancementsHelper;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.server.PlayerAdvancements;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Predicate;

/**
 * Reveals the entire vanilla advancement tree from the start, so players don't
 * have to unlock a prerequisite advancement before its children become visible
 * on the "L" screen.
 *
 * {@link net.minecraft.server.advancements.AdvancementVisibilityEvaluator} marks
 * an advancement with display info as visible whenever the supplied predicate
 * returns {@code true} (vanilla passes an "is this advancement done" predicate,
 * which is why undone branches stay hidden). We replace that predicate with an
 * always-true one when {@link RevealAllAdvancementsHelper#enabled} is set, which
 * is equivalent to the player having completed the whole tree for visibility
 * purposes — without touching actual progress. Advancements without display info
 * stay hidden, exactly as in vanilla.
 */
@Mixin(PlayerAdvancements.class)
public class PlayerAdvancementsRevealAllMixin {
    @ModifyArg(
            method = "updateTreeVisibility",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/advancements/AdvancementVisibilityEvaluator;evaluateVisibility(Lnet/minecraft/advancements/AdvancementNode;Ljava/util/function/Predicate;Lnet/minecraft/server/advancements/AdvancementVisibilityEvaluator$Output;)V"
            ),
            index = 1
    )
    private Predicate<AdvancementNode> exbingo$revealAll(Predicate<AdvancementNode> original) {
        if (RevealAllAdvancementsHelper.enabled) {
            return node -> true;
        }
        return original;
    }
}
