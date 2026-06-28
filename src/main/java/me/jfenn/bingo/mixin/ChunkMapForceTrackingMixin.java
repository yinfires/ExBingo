package me.jfenn.bingo.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import me.jfenn.bingo.impl.ChunkMapForceTracking;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.Set;

@Mixin(ChunkMap.class)
public abstract class ChunkMapForceTrackingMixin implements ChunkMapForceTracking {

    @Shadow
    @Final
    private Int2ObjectMap<?> entityMap;

    @Shadow
    protected abstract boolean isChunkTracked(ServerPlayer player, int x, int z);

    @Override
    public boolean exbingo_isPlayerTrackingReady(ServerPlayer player) {
        // The player must be registered in the tracker (so a re-pair can find its
        // TrackedEntity) AND the client must already hold the player's own chunk
        // (isChunkTracked == true: in the tracking view and no longer pending in
        // the chunk sender). Only then will a freshly-sent spawn packet actually
        // be rendered rather than discarded for a not-yet-loaded chunk.
        if (!this.entityMap.containsKey(player.getId())) return false;
        int x = SectionPos.blockToSectionCoord(player.getBlockX());
        int z = SectionPos.blockToSectionCoord(player.getBlockZ());
        return this.isChunkTracked(player, x, z);
    }

    @Override
    public void exbingo_forceAllPlayerPairings(List<ServerPlayer> players) {
        for (Object tracked : this.entityMap.values()) {
            TrackedEntityAccessor accessor = (TrackedEntityAccessor) tracked;
            Entity entity = accessor.exbingo$getEntity();
            if (!(entity instanceof ServerPlayer trackedPlayer)) continue;

            ServerEntity serverEntity = accessor.exbingo$getServerEntity();
            Set<ServerPlayerConnection> seenBy = accessor.exbingo$getSeenBy();

            for (ServerPlayer observer : players) {
                if (observer == trackedPlayer) continue;
                // Force a CLEAN re-pair, not a best-effort add. During the reset
                // teleport an early pairing may have already added the observer to
                // seenBy and sent a spawn packet — but at that moment the client
                // had not yet loaded the lobby chunk, so it discarded the entity.
                // seenBy still holds the connection, so a plain seenBy.add() would
                // return false and never re-send the spawn, leaving the player
                // permanently invisible until they move. Dropping the pairing first
                // and re-adding it guarantees a fresh spawn now that the client has
                // the chunk.
                if (seenBy.remove(observer.connection)) {
                    serverEntity.removePairing(observer);
                }
                seenBy.add(observer.connection);
                serverEntity.addPairing(observer);
            }
        }
    }
}
