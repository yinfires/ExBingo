package me.jfenn.bingo.mixin;

import me.jfenn.bingo.mixinhandler.ServerPlayNetworkHandlerMixinHandler;
import net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayNetworkHandlerMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(at = @At("HEAD"), method = "handleMovePlayer")
    private void onPlayerMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        ServerPlayNetworkHandlerMixinHandler.INSTANCE.onPlayerMove(player);
    }

    // The client sends this once per batch of chunks it has received and processed,
    // which is a direct signal that terrain has actually arrived on the client (unlike
    // movement packets, which only prove the client is alive). Used to gate the LOADING
    // -> PLAYING transition so high-latency clients aren't dropped into the world before
    // their spawn terrain has loaded (void, can't break blocks, others see them idle).
    @Inject(at = @At("HEAD"), method = "handleChunkBatchReceived")
    private void onChunkBatchReceived(ServerboundChunkBatchReceivedPacket packet, CallbackInfo ci) {
        ServerPlayNetworkHandlerMixinHandler.INSTANCE.onChunkBatchReceived(player);
    }
}
