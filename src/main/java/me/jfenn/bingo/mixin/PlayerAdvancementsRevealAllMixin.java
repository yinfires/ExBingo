package me.jfenn.bingo.mixin;

import me.jfenn.bingo.mixinhandler.RevealAllAdvancementsHelper;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Reveals the entire vanilla advancement tree from the start, so players don't
 * have to unlock a prerequisite advancement before its children become visible
 * on the "L" screen.
 *
 * Two things are needed:
 * <ol>
 *   <li>{@link #exbingo$queueAllRoots} — on the very first advancement packet,
 *       vanilla only evaluates the roots that happen to be queued in
 *       {@code rootsToUpdate}. On a brand-new world that set is almost empty
 *       (roots are only queued when an advancement gains saved progress or is
 *       completed), so most trees would never be sent. We force every root into
 *       the queue on the first packet (and on datapack reloads) so the whole
 *       tree gets evaluated.</li>
 *   <li>{@link #exbingo$revealAll} — {@link net.minecraft.server.advancements.AdvancementVisibilityEvaluator}
 *       marks an advancement with display info as visible whenever the supplied
 *       predicate returns {@code true}. Vanilla passes an "is this advancement
 *       done" predicate, which is why undone branches stay hidden. We replace it
 *       with an always-true predicate, equivalent (for visibility only) to the
 *       player having completed the whole tree. Actual progress is untouched and
 *       advancements without display info stay hidden, exactly as in vanilla.</li>
 * </ol>
 *
 * Both are gated on {@link RevealAllAdvancementsHelper#enabled}.
 */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsRevealAllMixin {
    @Shadow
    private AdvancementTree tree;

    @Shadow
    private boolean isFirstPacket;

    @Shadow
    @org.spongepowered.asm.mixin.Final
    private Set<AdvancementNode> rootsToUpdate;

    @Inject(method = "flushDirty", at = @At("HEAD"))
    private void exbingo$queueAllRoots(ServerPlayer serverPlayer, CallbackInfo ci) {
        if (RevealAllAdvancementsHelper.enabled && this.isFirstPacket) {
            for (AdvancementNode root : this.tree.roots()) {
                this.rootsToUpdate.add(root);
            }
        }
    }

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
