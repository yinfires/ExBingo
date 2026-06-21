package me.jfenn.bingo.mixin;

import me.jfenn.bingo.mixinhandler.PlayerManagerMixinHelper;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EndPortalBlock.class)
abstract class EndPortalBlockMixin {
    @Inject(
            method = "getPortalDestination",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;findRespawnPositionAndUseSpawnBlock(ZLnet/minecraft/world/level/portal/DimensionTransition$PostDimensionTransition;)Lnet/minecraft/world/level/portal/DimensionTransition;")
    )
    public void createTeleportTargetForEndSpawn(
            ServerLevel world,
            Entity entity,
            BlockPos pos,
            CallbackInfoReturnable<DimensionTransition> cir
    ) {
        if (entity instanceof ServerPlayer player) {
            // the player is exiting through the end portal...
            // if the spawnpoint is also in the end, then respawn them in the overworld
            // NOTE: duplicated inside PlayerManagerMixin
            if (PlayerManagerMixinHelper.Companion.shouldOverrideEndRespawn()) {
                var overworld = world.getServer().overworld();
                player.setRespawnPosition(overworld.dimension(), overworld.getSharedSpawnPos(), overworld.getSharedSpawnAngle(), false, false);
            }
        }
    }
}
