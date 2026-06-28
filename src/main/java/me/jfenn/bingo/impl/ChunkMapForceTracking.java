package me.jfenn.bingo.impl;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Duck interface implemented (via mixin) by {@code net.minecraft.server.level.ChunkMap}.
 * Lives OUTSIDE the mixin package on purpose: the mixin transformer refuses to
 * let other classes reference types that live in a mixin-owned package, so the
 * publicly-referenced interface must sit here while the mixin itself stays under
 * {@code me.jfenn.bingo.mixin}.
 */
public interface ChunkMapForceTracking {
    /**
     * True once [player] is registered in the tracker AND its own chunk has been
     * delivered to the client (so a re-sent spawn will render, not be discarded).
     * Callers should wait for this before forcing a re-pair after a teleport.
     */
    boolean exbingo_isPlayerTrackingReady(ServerPlayer player);

    /**
     * Force bilateral entity-tracker pairings between every player in [players],
     * bypassing the {@code isChunkTracked} gate (which rejects pairings while the
     * target chunk is still pending in the client's chunk sender).
     *
     * The caller MUST ensure each player is already registered in the tracker's
     * entityMap first (i.e. their entity section is accessible) — otherwise the
     * iteration finds no TrackedEntity for them and pairs nothing.
     */
    void exbingo_forceAllPlayerPairings(List<ServerPlayer> players);
}
