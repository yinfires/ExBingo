package me.jfenn.bingo.common.menu

import me.jfenn.bingo.common.event.InteractionEntityEvents
import me.jfenn.bingo.platform.EntityType
import me.jfenn.bingo.platform.IEntity
import me.jfenn.bingo.platform.IEntityManager
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.TicketType
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.chunk.status.ChunkStatus
import org.joml.Matrix4d
import org.joml.Vector3d
import org.koin.core.scope.Scope
import org.slf4j.Logger
import java.util.*
import kotlin.math.roundToInt

internal class MenuInstance(
    private val log: Logger,
    koinScope: Scope,
    private val world: ServerLevel,
    private val entityManager: IEntityManager,
    private val interactionEntityEvents: InteractionEntityEvents,
    private val matrix: Matrix4d,
    private val instanceTag: String,
) {

    private val failedSpawnAttempts = mutableMapOf<UUID, Int>()
    private val loggedSpawnFailures = mutableSetOf<UUID>()
    private val menuChunks = mutableSetOf<ChunkPos>()

    private companion object {
        // Keeps menu chunks loaded so display/interaction entities remain available while the
        // lobby menu is active.
        val MENU_TICKET: TicketType<ChunkPos> = TicketType.create("bingo-menu") { a, b -> a.toLong().compareTo(b.toLong()) }
    }

    private val menuComponent = component(koinScope) {
        val translation = Vector3d(0.5, 2.0, 0.05)
        if (matrix.get(0, 0) == -1.0 && matrix.get(1, 1) == 1.0 && matrix.get(2, 2) == -1.0) {
            // Fix for menu position being wacky in this specific rotation
            // (euler angles turn into x=180, y=0 here - even though it should be y=180)
            translation.add(-1.0, 0.0, -1.0)
        }

        registerMenuPage(translation)
    }

    fun tick() {
        menuComponent.tick(this)
    }

    fun markDirty() {
        failedSpawnAttempts.clear()
        menuComponent.markDirty()
    }

    fun cleanup() {
        menuComponent.despawn()
        releaseMenuTickets()
    }

    private fun IEntity.resetPos() {
        pos = Vector3d()
        yaw = 0f
    }

    private fun IEntity.transformPos() {
        pos = matrix.transformPosition(pos)

        val angles = Vector3d()
        matrix.getEulerAnglesXYZ(angles)

        var yawDegrees = yaw + Math.toDegrees(-angles.x - angles.y).roundToInt()
        while (yawDegrees < 0) yawDegrees += 360f

        if (matrix.get(0, 0) == 0.0 && matrix.get(1, 1) == 1.0 && matrix.get(2, 2) == 0.0) {
            // Fix for menu position being wacky in this specific rotation
            yawDegrees *= -1
        }

        yaw = yawDegrees
    }

    private fun loadChunkAt(pos: Vector3d) {
        val chunkPos = ChunkPos(BlockPos(pos.x.toInt(), 0, pos.z.toInt()))
        if (!menuChunks.add(chunkPos)) return

        world.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FULL)
        world.chunkSource.addRegionTicket(MENU_TICKET, chunkPos, 0, chunkPos)
    }

    private fun releaseMenuTickets() {
        for (chunkPos in menuChunks) {
            world.chunkSource.removeRegionTicket(MENU_TICKET, chunkPos, 0, chunkPos)
        }
        menuChunks.clear()
    }

    private fun Vector3d.isNear(other: Vector3d): Boolean {
        return distanceSquared(other) < 1.0E-4
    }

    private fun isMenuEntity(type: EntityType<*>) =
        type == EntityType.TEXT_DISPLAY ||
                type == EntityType.BLOCK_DISPLAY ||
                type == EntityType.INTERACTION

    @Suppress("UNCHECKED_CAST")
    private fun createCandidate(info: MenuEntityHandle<*>): IEntity {
        return createTypedCandidate(info as MenuEntityHandle<IEntity>)
    }

    private fun <T : IEntity> createTypedCandidate(info: MenuEntityHandle<T>): T {
        val candidate = entityManager.createEntity(info.type, world)
        candidate.commandTags = setOf(instanceTag)
        candidate.resetPos()
        info.init.invoke(candidate)
        candidate.transformPos()
        return candidate
    }

    fun prepareSpawn(infos: Collection<MenuEntityHandle<*>>) {
        val candidates = infos.map(::createCandidate)
        try {
            candidates.forEach { loadChunkAt(it.pos) }
            cleanupStaleEntities(candidates, infos.mapNotNull { it.entityId }.toSet())
        } finally {
            candidates.forEach { it.discard() }
        }
    }

    private fun cleanupStaleEntities(candidates: List<IEntity>, runtimeIds: Set<UUID>) {
        val menuCandidates = candidates.filter { isMenuEntity(it.type) }
        if (menuCandidates.isEmpty()) return

        entityManager.iterateEntities(world)
            .filter { it.uuid !in runtimeIds }
            .filter { stale -> menuCandidates.any { it.type == stale.type && it.pos.isNear(stale.pos) } }
            .filter {
                val bingoTags = it.commandTags.filter { tag -> tag.startsWith("bingo-") }
                bingoTags.isEmpty() || instanceTag !in bingoTags
            }
            .forEach { stale ->
                interactionEntityEvents.removeInteract(stale.uuid)
                stale.discard()
            }
    }

    fun <T: IEntity> spawn(info: MenuEntityHandle<T>): T? {
        val candidate = createTypedCandidate(info)
        loadChunkAt(candidate.pos)

        @Suppress("UNCHECKED_CAST")
        var existing: T? = info.entityId
            ?.let { entityManager.getEntity(world, it) }
            ?.takeIf { it.type == info.type }
            as? T

        if (existing == null) {
            info.entityId?.let(interactionEntityEvents::removeInteract)
            info.entityId = null
        }

        if (existing != null && (!existing.commandTags.contains(instanceTag) || !existing.pos.isNear(candidate.pos))) {
            interactionEntityEvents.removeInteract(existing.uuid)
            existing.discard()
            existing = null
            info.entityId = null
        }

        val entity: T
        if (existing == null) {
            entity = candidate

            if (!entityManager.spawnEntity(world, entity)) {
                if (loggedSpawnFailures.add(info.id)) {
                    log.warn("Unable to spawn {} menu entity at {} - will retry with backoff", info.type, entity.pos)
                }
                entity.discard()
                failedSpawnAttempts[info.id] = (failedSpawnAttempts[info.id] ?: 0) + 1
                return null
            }

            info.entityId = entity.uuid
            failedSpawnAttempts.remove(info.id)
            loggedSpawnFailures.remove(info.id)
        } else {
            candidate.discard()
            entity = existing
            entity.commandTags = setOf(instanceTag)
            entity.resetPos()
            info.init.invoke(entity)
            entity.transformPos()
        }

        if (entity.type != info.type) {
            log.error("Entity type ${info.type} does not match the entity being updated")
            return null
        }

        info.onUpdate(entity)
        entityManager.syncEntityData(entity)
        return entity
    }

    fun retryDelayTicks(): Int {
        val attempts = failedSpawnAttempts.values.maxOrNull() ?: return 1
        // Keep retries quick so transient spawn ordering issues resolve without leaving visible gaps.
        return when {
            attempts <= 20 -> 2
            attempts <= 60 -> 10
            else -> 40
        }
    }

    fun despawn(info: MenuEntityHandle<*>) {
        val entityId = info.entityId ?: info.id
        interactionEntityEvents.removeInteract(entityId)
        val entity = entityManager.getEntity(world, entityId)
        entity?.discard()
        info.entityId = null
    }

}
