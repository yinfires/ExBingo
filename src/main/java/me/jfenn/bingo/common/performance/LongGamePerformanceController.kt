package me.jfenn.bingo.common.performance

import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.state.BingoState
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.Projectile
import org.slf4j.Logger
import kotlin.math.max

class LongGamePerformanceController(
    private val config: BingoConfig,
    private val state: BingoState,
    private val server: MinecraftServer,
    private val events: ScopedEvents,
    private val log: Logger,
) {
    private val cleanupConfig get() = config.server.performanceCleanup
    private var nextCleanupTick = 0

    init {
        events.onUpdateTick { event ->
            val cleanup = cleanupConfig
            if (!cleanup.enabled || !state.state.isPlayingOrCountdown) {
                return@onUpdateTick
            }

            if (event.ticks < nextCleanupTick) {
                return@onUpdateTick
            }

            nextCleanupTick = event.ticks + max(20, cleanup.intervalTicks)
            cleanupFarEntities()
        }
    }

    private fun cleanupFarEntities() {
        val cleanup = cleanupConfig
        val players = server.playerList.players
            .filter { it.level() is ServerLevel }

        if (players.isEmpty()) {
            return
        }

        val totalEntities = server.allLevels.sumOf { level ->
            level.allEntities.count()
        }

        if (totalEntities < cleanup.minTotalEntities) {
            return
        }

        var removed = 0
        val removedByType = linkedMapOf<String, Int>()
        val removedByWorld = linkedMapOf<String, Int>()
        val maxPerPass = max(1, cleanup.maxEntitiesPerPass)

        for (level in server.allLevels) {
            if (removed >= maxPerPass) break

            val candidates = level.allEntities
                .asSequence()
                .filter { shouldRemove(it, level) }
                .take(maxPerPass - removed)
                .toList()

            for (entity in candidates) {
                val type = entity.type.builtInRegistryHolder().key().location().toString()
                val world = level.dimension().location().toString()
                entity.discard()
                removed++
                removedByType[type] = (removedByType[type] ?: 0) + 1
                removedByWorld[world] = (removedByWorld[world] ?: 0) + 1
            }
        }

        if (removed > 0) {
            log.info(
                "[PerformanceCleanup] Removed {} far entities (totalBefore={}, keepRadius={} chunks, byWorld={}, topTypes={})",
                removed,
                totalEntities,
                cleanup.keepChunkRadius,
                removedByWorld.entries.joinToString { "${it.key}=${it.value}" },
                removedByType.entries
                    .sortedByDescending { it.value }
                    .take(8)
                    .joinToString { "${it.key}=${it.value}" },
            )
        }
    }

    private fun shouldRemove(entity: Entity, level: ServerLevel): Boolean {
        val cleanup = cleanupConfig
        if (entity is Player) return false
        if (entity.isVehicle || entity.isPassenger) return false
        if (entity.tags.any { it.startsWith("bingo-") }) return false
        if (isProtectedEntity(entity)) return false
        if (isNearAnyPlayer(entity, level)) return false
        if (isPersistent(entity)) return false

        return when {
            cleanup.cleanupItems && entity is ItemEntity -> true
            cleanup.cleanupProjectiles && entity is Projectile -> true
            cleanup.cleanupMobs && entity is Mob -> true
            cleanup.cleanupMultipartEntities && isMultipartEntity(entity) -> true
            else -> false
        }
    }

    private fun isNearAnyPlayer(entity: Entity, level: ServerLevel): Boolean {
        val radius = cleanupConfig.keepChunkRadius
        val entityChunk = entity.chunkPosition()
        return server.playerList.players.any { player ->
            player.level() == level &&
                    kotlin.math.abs(player.chunkPosition().x - entityChunk.x) <= radius &&
                    kotlin.math.abs(player.chunkPosition().z - entityChunk.z) <= radius
        }
    }

    private fun isProtectedEntity(entity: Entity): Boolean {
        val key = entity.type.builtInRegistryHolder().key().location().toString()
        val namespace = key.substringBefore(':')
        return cleanupConfig.protectedEntityNamespaces.any { protected ->
            protected == key || protected == namespace
        }
    }

    private fun isPersistent(entity: Entity): Boolean {
        if (entity.hasCustomName()) return true
        val tag = CompoundTag()
        entity.saveWithoutId(tag)
        return tag.getBoolean("PersistenceRequired") ||
                tag.getBoolean("Invulnerable") ||
                tag.contains("Leash")
    }

    private fun isMultipartEntity(entity: Entity): Boolean {
        val key = entity.type.builtInRegistryHolder().key().location().toString()
        return "multipart" in key || entity.javaClass.name.contains("Multipart", ignoreCase = true)
    }
}
