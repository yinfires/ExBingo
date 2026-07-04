package me.jfenn.bingo.mixinhandler

import net.minecraft.server.level.ServerPlayer
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

object ServerPlayNetworkHandlerMixinHandler {

    private val playerMap = ConcurrentHashMap<Int, Instant>()
    private val chunkBatchMap = ConcurrentHashMap<Int, Instant>()

    fun getLastPlayerMovement(player: ServerPlayer): Instant? {
        val id = System.identityHashCode(player)
        return playerMap[id]
    }

    fun onPlayerMove(player: ServerPlayer) {
        val id = System.identityHashCode(player)
        playerMap[id] = Instant.now()
    }

    /**
     * The last time the client acknowledged receiving a batch of chunk data. This is a
     * direct signal that terrain has arrived on the client, used to gate LOADING -> PLAYING.
     */
    fun getLastChunkBatchReceived(player: ServerPlayer): Instant? {
        val id = System.identityHashCode(player)
        return chunkBatchMap[id]
    }

    fun onChunkBatchReceived(player: ServerPlayer) {
        val id = System.identityHashCode(player)
        chunkBatchMap[id] = Instant.now()
    }
}