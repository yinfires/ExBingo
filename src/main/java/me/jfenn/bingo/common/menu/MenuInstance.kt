package me.jfenn.bingo.common.menu

import me.jfenn.bingo.common.event.InteractionEntityEvents
import me.jfenn.bingo.platform.IEntity
import me.jfenn.bingo.platform.IEntityManager
import net.minecraft.server.level.ServerLevel
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

    private fun loadChunkFor(entity: IEntity) {
        val chunkPos = ChunkPos(BlockPos(entity.pos.x.toInt(), 0, entity.pos.z.toInt()))
        world.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FULL)
    }

    fun <T: IEntity> spawn(info: MenuEntityHandle<T>): T? {
        @Suppress("UNCHECKED_CAST")
        var entity: T? = entityManager.getEntity(world, info.id)
            ?.takeIf { it.type == info.type }
                as? T

        if (entity == null) {
            entity = entityManager.createEntity(info.type, world)
            entity.uuid = info.id
            entity.commandTags = setOf(instanceTag)
            entity.resetPos()
            info.init.invoke(entity)
            entity.transformPos()
            loadChunkFor(entity)

            // Try to spawn the entity into the world...
            val isSuccess = entityManager.spawnEntity(world, entity)
            // If the entity cannot be spawned, discard it and return null
            // (the MenuComponent will retry with backoff instead of trying every tick)
            if (!isSuccess) {
                if (loggedSpawnFailures.add(info.id)) {
                    log.warn("Unable to spawn {} menu entity at {} - will retry with backoff", info.type, entity.pos)
                }
                entity.discard()
                failedSpawnAttempts[info.id] = (failedSpawnAttempts[info.id] ?: 0) + 1
                return null
            }

            failedSpawnAttempts.remove(info.id)
            loggedSpawnFailures.remove(info.id)
        } else {
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
        return entity
    }

    fun retryDelayTicks(): Int {
        val attempts = failedSpawnAttempts.values.maxOrNull() ?: return 1
        return when {
            attempts <= 2 -> 10
            attempts <= 5 -> 40
            else -> 100
        }
    }

    fun despawn(info: MenuEntityHandle<*>) {
        interactionEntityEvents.INTERACT_LISTENERS.remove(info.id)
        val entity = entityManager.getEntity(world, info.id)
        entity?.discard()
    }

}
