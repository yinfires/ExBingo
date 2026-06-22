package me.jfenn.bingo.mixin;

import me.jfenn.bingo.common.spawn.SpawnData;
import me.jfenn.bingo.mixinhandler.PlayerManagerMixinHelper;
import me.jfenn.bingo.platform.block.BlockPosition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(PlayerList.class)
public abstract class PlayerManagerMixin {

    @Shadow @Final private MinecraftServer server;

    @Unique
    private final Logger log = LoggerFactory.getLogger(PlayerManagerMixin.class);

    @Inject(
            at = @At(value = "HEAD"),
            method = "broadcast(Lnet/minecraft/world/entity/player/Player;DDDDLnet/minecraft/resources/ResourceKey;Lnet/minecraft/network/protocol/Packet;)V",
            cancellable = true
    )
    private void sendToAround(@Nullable Player player, double x, double y, double z, double distance, ResourceKey<Level> worldKey, Packet<?> packet, CallbackInfo ci) {
        // If the game is in PREGAME, prevent chaos by cancelling sound packets while in the lobby
        if (packet instanceof ClientboundSoundPacket && PlayerManagerMixinHelper.Companion.shouldPreventLobbyChaos()) {
            ci.cancel();
        }
    }

    @Inject(at = @At(value = "RETURN"), method = "load", cancellable = true)
    private void loadPlayerData(ServerPlayer player, CallbackInfoReturnable<Optional<CompoundTag>> ci) {
        // Provide default player data (for the lobby dimension / spawnpoint) if null
        if (ci.getReturnValue().isEmpty() && PlayerManagerMixinHelper.Companion.shouldSpawnInLobby()) {
            SpawnData spawn = PlayerManagerMixinHelper.Companion.getPregameSpawnData();
            if (spawn == null) return;

            CompoundTag nbt = new CompoundTag();
            nbt.putString("Dimension", spawn.getDimension());

            ListTag position = new ListTag();
            BlockPosition blockPosition = spawn.getPosition();
            position.add(DoubleTag.valueOf(blockPosition.getX() + 0.5));
            position.add(DoubleTag.valueOf(blockPosition.getY()));
            position.add(DoubleTag.valueOf(blockPosition.getZ() + 0.5));
            nbt.put("Pos", position);

            ListTag rotation = new ListTag();
            rotation.add(FloatTag.valueOf(spawn.getYaw())); // yaw
            rotation.add(FloatTag.valueOf(0f)); // pitch
            nbt.put("Rotation", rotation);

            player.load(nbt);
            ci.setReturnValue(Optional.of(nbt));
        }
    }

    @Inject(at = @At(value = "HEAD"), method = "respawn")
    private void respawnPlayer(ServerPlayer player, boolean alive, Entity.RemovalReason removalReason, CallbackInfoReturnable<ServerPlayer> info) {
        if (!PlayerManagerMixinHelper.Companion.exists()) return;

        if (alive) {
            // the player is exiting through the end portal...
            // if the spawnpoint is also in the end, then respawn them in the overworld
            // NOTE: duplicated inside EndPortalBlockMixin
            if (PlayerManagerMixinHelper.Companion.shouldOverrideEndRespawn()) {
                var overworld = player.server.overworld();
                player.setRespawnPosition(overworld.dimension(), overworld.getSharedSpawnPos(), overworld.getSharedSpawnAngle(), false, false);
            }
            return;
        }

        // If the spawnpoint is a respawn anchor, get the current state
        // (this will be affected by getRespawnTarget)
        ServerLevel respawnAnchorWorld = server.getLevel(player.getRespawnDimension());
        BlockPos respawnAnchorBlockPos = player.getRespawnPosition();
        BlockState respawnAnchorBlockState = respawnAnchorWorld != null && respawnAnchorBlockPos != null
                ? respawnAnchorWorld.getBlockState(respawnAnchorBlockPos)
                : null;
        if (respawnAnchorBlockState == null || !(respawnAnchorBlockState.getBlock() instanceof RespawnAnchorBlock)) {
            // Indicates that this is not a respawn anchor block
            respawnAnchorWorld = null;
        }

        // target can be null if the player's spawnpoint failed... so it should default to the team spawn
        boolean missingRespawnBlock = false;
        try {
            var target = player.findRespawnPositionAndUseSpawnBlock(alive, DimensionTransition.DO_NOTHING);
            missingRespawnBlock = target == null || target.missingRespawnBlock();
        } catch (Throwable e) {
            log.error("Exception thrown during Player.getRespawnTarget", e);
        }

        // Reset the respawn anchor to the state captured before getRespawnTarget was called
        if (respawnAnchorWorld != null) {
            respawnAnchorWorld.setBlock(respawnAnchorBlockPos, respawnAnchorBlockState, 3);
        }

        // if the targeted spawnpoint is invalid (i.e. a broken bed, or otherwise somewhere that would reset to the world spawn) ...
        if (missingRespawnBlock) {
            // change the player's spawnpoint back to the team spawn location
            PlayerManagerMixinHelper.Companion.setPlayerSpawnpoint(player);
        }
    }
}
