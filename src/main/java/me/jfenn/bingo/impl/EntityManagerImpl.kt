package me.jfenn.bingo.impl

import me.jfenn.bingo.common.utils.toNbt
import me.jfenn.bingo.platform.*
import me.jfenn.bingo.platform.item.IItemStack
import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.text.ITextFactory
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.EntityType as MinecraftEntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.PrimedTnt
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Display.BlockDisplay
import net.minecraft.world.entity.Display.TextDisplay
import net.minecraft.world.entity.Interaction
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.ambient.Bat
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import org.joml.Matrix4f
import org.joml.Vector3d
import java.util.*
import kotlin.reflect.KProperty

class EntityManagerImpl(
    private val textFactory: ITextFactory,
    private val textSerializer: ITextSerializer,
) : IEntityManager {
    private fun EntityType<*>.toMinecraftType(): MinecraftEntityType<*> {
        return when (this) {
            EntityType.INTERACTION -> MinecraftEntityType.INTERACTION
            EntityType.TEXT_DISPLAY -> MinecraftEntityType.TEXT_DISPLAY
            EntityType.BLOCK_DISPLAY -> MinecraftEntityType.BLOCK_DISPLAY
            EntityType.ARMOR_STAND -> MinecraftEntityType.ARMOR_STAND
            EntityType.TNT -> MinecraftEntityType.TNT
            EntityType.BAT -> MinecraftEntityType.BAT
            else -> error("Entity type not recognized!")
        }
    }

    private fun forEntity(entity: Entity): IEntity {
        val impl = when (entity) {
            is Interaction -> InteractionEntityImpl(entity)
            is TextDisplay -> TextDisplayEntityImpl(entity, textFactory, textSerializer)
            is BlockDisplay -> BlockDisplayEntityImpl(entity)
            is ArmorStand -> ArmorStandEntityImpl(entity)
            is PrimedTnt -> TntEntityImpl(entity)
            is Bat -> BatEntityImpl(entity)
            is LivingEntity -> LivingEntityImpl(entity)
            else -> EntityImpl(entity)
        }
        return impl
    }

    override fun <T : IEntity> createEntity(type: EntityType<T>, world: ServerLevel): T {
        val mcType = type.toMinecraftType()
        val entity = mcType.create(world)!!
        val impl = forEntity(entity)
        return (@Suppress("UNCHECKED_CAST") (impl as T))
    }

    override fun getEntity(world: ServerLevel, uuid: UUID): IEntity? {
        val entity = world.getEntity(uuid) ?: return null
        return forEntity(entity)
    }

    override fun spawnEntity(world: ServerLevel, entity: IEntity): Boolean {
        return world.addFreshEntity(entity.entity)
    }

    override fun syncEntityData(entity: IEntity) {
        val minecraftEntity = entity.entity
        val dataValues = minecraftEntity.entityData.packDirty()
            ?: minecraftEntity.entityData.nonDefaultValues
            ?: return
        val level = minecraftEntity.level() as? ServerLevel ?: return
        level.chunkSource.broadcastAndSend(minecraftEntity, ClientboundSetEntityDataPacket(minecraftEntity.id, dataValues))
    }

    override fun iterateEntities(world: ServerLevel): Sequence<IEntity> {
        return world.allEntities
            .asSequence()
            .map { forEntity(it) }
    }
}

private class NbtBooleanDelegate(private val name: String) {
    operator fun getValue(thisRef: EntityImpl, property: KProperty<*>): Boolean = thisRef.nbt.getBoolean(name)
    operator fun setValue(thisRef: EntityImpl, property: KProperty<*>, value: Boolean) {
        return thisRef.patchNbt { putBoolean(name, value) }
    }
}

private class NbtIntDelegate(private val name: String) {
    operator fun getValue(thisRef: EntityImpl, property: KProperty<*>): Int = thisRef.nbt.getInt(name)
    operator fun setValue(thisRef: EntityImpl, property: KProperty<*>, value: Int) {
        return thisRef.patchNbt { putInt(name, value) }
    }
}

private class NbtFloatDelegate(private val name: String) {
    operator fun getValue(thisRef: EntityImpl, property: KProperty<*>): Float = thisRef.nbt.getFloat(name)
    operator fun setValue(thisRef: EntityImpl, property: KProperty<*>, value: Float) {
        return thisRef.patchNbt { putFloat(name, value) }
    }
}

open class EntityImpl(
    override val entity: Entity
) : IEntity {
    override var uuid: UUID
        get() = entity.uuid
        set(value) {
            entity.uuid = value
        }

    override var pos: Vector3d
        get() = entity.position().let { Vector3d(it.x, it.y, it.z) }
        set(value) {
            entity.setPos(value.x, value.y, value.z)
        }

    override var commandTags: Set<String>
        get() = entity.tags.toSet()
        set(value) {
            entity.tags.toSet().forEach { entity.removeTag(it) }
            value.forEach { entity.addTag(it) }
        }

    override var pitch: Float
        get() = entity.xRot
        set(value) {
            entity.xRot = value
        }

    override var yaw: Float
        get() = entity.yRot
        set(value) {
            entity.yRot = value
        }

    override fun discard() {
        entity.discard()
    }

    internal var nbt
        get() = CompoundTag().also { entity.saveWithoutId(it) }
        set(value) {
            entity.load(value)
        }

    internal fun <R> patchNbt(patch: CompoundTag.() -> R): R {
        val compound = nbt
        val ret = patch(compound)
        nbt = compound
        return ret
    }
}

class InteractionEntityImpl(
    override val entity: Interaction
) : IInteractionEntity, EntityImpl(entity) {
    override val type: EntityType<IInteractionEntity>
        get() = EntityType.INTERACTION
    override var width: Float by NbtFloatDelegate("width")
    override var height: Float by NbtFloatDelegate("height")
}

open class DisplayEntityImpl(
    override val entity: Display
) : IDisplayEntity, EntityImpl(entity) {
    override var transformation: Matrix4f
        get() = Matrix4f()
        set(value) {
            patchNbt { put("transformation", value.toNbt()) }
        }

    override var brightness: IDisplayEntity.Brightness?
        get() = nbt
            .takeIf { it.contains("brightness") }
            ?.getCompound("brightness")
            ?.let {
                IDisplayEntity.Brightness(
                    block = it.getInt("block"),
                    sky = it.getInt("sky"),
                )
            }
        set(value) {
            patchNbt {
                if (value != null) {
                    put("brightness", CompoundTag().apply {
                        putInt("block", value.block)
                        putInt("sky", value.sky)
                    })
                } else {
                    remove("brightness")
                }
            }
        }
}

class TextDisplayEntityImpl(
    override val entity: TextDisplay,
    private val textFactory: ITextFactory,
    private val textSerializer: ITextSerializer,
) : ITextDisplayEntity, DisplayEntityImpl(entity) {
    override val type: EntityType<ITextDisplayEntity>
        get() = EntityType.TEXT_DISPLAY

    override var value: IText
        get() = nbt.getString("text")
            ?.let { textSerializer.fromJson(it) }
            ?.let { textFactory.from(it) }
            ?: textFactory.empty()
        set(value) {
            patchNbt {
                putString("text", textSerializer.toJson(value.value))
            }
        }

    override var lineWidth: Int by NbtIntDelegate("line_width")

    override var billboard: ITextDisplayEntity.Billboard
        get() = nbt.getString("billboard")
            .uppercase()
            .let { ITextDisplayEntity.Billboard.valueOf(it) }
        set(value) {
            patchNbt { putString("billboard", value.name.lowercase()) }
        }

    override var alignment: ITextDisplayEntity.TextAlignment
        get() = nbt.getString("alignment")
            .uppercase()
            .let { ITextDisplayEntity.TextAlignment.valueOf(it) }
        set(value) {
            patchNbt { putString("alignment", value.name.lowercase()) }
        }

    override var background: Int by NbtIntDelegate("background")
    override var shadow: Boolean by NbtBooleanDelegate("shadow")
}

class BlockDisplayEntityImpl(
    override val entity: BlockDisplay,
) : IBlockDisplayEntity, DisplayEntityImpl(entity) {
    override val type: EntityType<IBlockDisplayEntity>
        get() = EntityType.BLOCK_DISPLAY

    override var blockIdentifier: String
        get() = nbt.getCompound("block_state").getString("Name")
        set(value) {
            patchNbt {
                put("block_state", CompoundTag().apply {
                    putString("Name", value)
                })
            }
        }
}

class ArmorStandEntityImpl(
    override val entity: ArmorStand,
) : IArmorStandEntity, EntityImpl(entity) {
    override fun equipStack(slot: IArmorStandEntity.EquipmentSlot, stack: IItemStack) {
        val mcSlot = when (slot) {
            IArmorStandEntity.EquipmentSlot.MAINHAND -> EquipmentSlot.MAINHAND
            IArmorStandEntity.EquipmentSlot.OFFHAND -> EquipmentSlot.OFFHAND
            IArmorStandEntity.EquipmentSlot.HEAD -> EquipmentSlot.HEAD
            IArmorStandEntity.EquipmentSlot.CHEST -> EquipmentSlot.CHEST
            IArmorStandEntity.EquipmentSlot.LEGS -> EquipmentSlot.LEGS
            IArmorStandEntity.EquipmentSlot.FEET -> EquipmentSlot.FEET
        }
        entity.setItemSlot(mcSlot, stack.stack)
    }
}

class TntEntityImpl(
    override val entity: PrimedTnt,
) : ITntEntity, EntityImpl(entity) {
    override var fuse: Int
        get() = entity.fuse
        set(value) { entity.fuse = value }
}

open class LivingEntityImpl(
    final override val entity: LivingEntity
) : ILivingEntity, EntityImpl(entity) {
    override var invulnerable: Boolean by NbtBooleanDelegate("Invulnerable")
    override var noGravity: Boolean by NbtBooleanDelegate("NoGravity")
    override var silent: Boolean by NbtBooleanDelegate("Silent")
    override var noAI: Boolean by NbtBooleanDelegate("NoAI")
    override var persistenceRequired: Boolean by NbtBooleanDelegate("PersistenceRequired")

    override var health: Float
        get() = entity.health
        set(value) { entity.health = value }
    override val maxHealth: Float
        get() = entity.maxHealth

    override var air: Int
        get() = entity.airSupply
        set(value) { entity.airSupply = value }
    override val maxAir: Int
        get() = entity.maxAirSupply

    override fun addEffect(
        type: EffectType,
        duration: Int,
        amplifier: Int,
        ambient: Boolean,
        visible: Boolean
    ): IStatusEffectHandle {
        val instance = MobEffectInstance(
            when (type) {
                EffectType.NIGHT_VISION -> MobEffects.NIGHT_VISION
                EffectType.SLOWNESS -> MobEffects.MOVEMENT_SLOWDOWN
                EffectType.JUMP_BOOST -> MobEffects.JUMP
                EffectType.INVISIBILITY -> MobEffects.INVISIBILITY
                EffectType.OTHER -> throw IllegalArgumentException("[StatusEffectsImpl] OTHER is not a valid effect type!")
            },
            duration,
            amplifier,
            ambient,
            visible
        )
        entity.addEffect(instance)
        return StatusEffectHandle(instance)
    }

    override fun getEffects(): List<IStatusEffectHandle> {
        return entity.activeEffects.map { StatusEffectHandle(it) }
    }

    override fun removeEffect(effect: IStatusEffectHandle) {
        require(effect is StatusEffectHandle)
        entity.removeEffect(effect.instance.effect)
    }
}

class BatEntityImpl(
    entity: Bat
) : IBatEntity, LivingEntityImpl(entity)
