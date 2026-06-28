package me.jfenn.bingo.impl

import com.mojang.authlib.GameProfile
import me.jfenn.bingo.impl.inventory.ContainerItemView
import me.jfenn.bingo.platform.*
import me.jfenn.bingo.platform.block.BlockPosition
import me.jfenn.bingo.platform.commands.ISignedMessage
import me.jfenn.bingo.platform.inventory.IContainerItemView
import me.jfenn.bingo.platform.item.IItemStack
import me.jfenn.bingo.platform.item.IItemStackFactory
import me.jfenn.bingo.platform.player.PlayerProfile
import me.jfenn.bingo.platform.scope.BingoKoin
import me.jfenn.bingo.platform.text.IText
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.*
import net.minecraft.network.chat.ChatType
import net.minecraft.network.chat.OutgoingChatMessage
import net.minecraft.server.level.ClientInformation
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket
import net.minecraft.network.protocol.game.ClientboundGameEventPacket
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.network.protocol.game.ClientboundRespawnPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.TicketType
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource
import net.minecraft.sounds.SoundEvents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.UseAnim
import net.minecraft.world.phys.HitResult
import net.minecraft.world.level.GameType
import net.minecraft.world.level.ChunkPos
import org.joml.Vector3d
import java.util.*

class PlayerManager(
    private val server: MinecraftServer,
    private val itemStackFactory: IItemStackFactory,
) : IPlayerManager {
    override fun forPlayer(player: ServerPlayer): IPlayerHandle {
        return PlayerHandle(player, itemStackFactory)
    }

    override fun getPlayer(uuid: UUID): IPlayerHandle? {
        return server.playerList.getPlayer(uuid)
            ?.let { PlayerHandle(it, itemStackFactory) }
    }

    override fun getPlayers(): List<IPlayerHandle> {
        return server.playerList.players
            .map { PlayerHandle(it, itemStackFactory) }
    }

    override fun updatePlayerListName(player: IPlayerHandle) {
        val packet = ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, player.player)
        server.playerList.broadcastAll(packet)
    }

    override fun getOfflinePlayer(profile: PlayerProfile): IPlayerHandle {
        val player = ServerPlayer(
            server,
            server.overworld(),
            GameProfile(profile.uuid, profile.name),
            ClientInformation.createDefault(),
        )

        server.playerList.load(player)
        return PlayerHandle(player, itemStackFactory)
    }

    override fun playToAround(
        player: ServerPlayer?,
        sound: PlayerSoundEvent,
        category: PlayerSoundCategory,
        volume: Float,
        pitch: Float,
        position: Triple<Double, Double, Double>,
        world: ServerLevel,
    ) {
        val (x, y, z) = position
        val soundEntry = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound.toSoundEvent())
        val packet = ClientboundSoundPacket(soundEntry, category.toSoundCategory(), x, y, z, volume, pitch, server.overworld().seed)
        player?.connection?.send(packet)
        server.playerList.broadcast(player, x, y, z, soundEntry.value().getRange(volume).toDouble(), world.dimension(), packet)
    }

    override fun broadcastChatMessage(message: ISignedMessage, sender: IPlayerHandle) {
        require(message is SignedMessageImpl)
        require(sender is PlayerHandle)

        val bound = ChatType.bind(ChatType.CHAT, sender.player)
        server.playerList.broadcastChatMessage(message.message, sender.player, bound)
    }
}

class PlayerHandle(
    override val player: ServerPlayer,
    private val itemStackFactory: IItemStackFactory? = null,
) : IPlayerHandle, LivingEntityImpl(player) {

    private fun getItemStackFactory() = itemStackFactory
        ?: BingoKoin.getScope(player.server)!!.get<IItemStackFactory>()

    override val world: IServerWorld
        get() = ServerWorldImpl(player.serverLevel())

    override val server: IMinecraftServer?
        get() = player.server?.let { MinecraftServerImpl(it) }

    override val isAlive: Boolean
        get() = player.isAlive
    override val isSpectator: Boolean
        get() = player.isSpectator

    override val playerName: String
        get() = player.scoreboardName

    override val commandSource: SharedSuggestionProvider
        get() = player.createCommandSourceStack()

    override var fireTicks: Int
        get() = player.remainingFireTicks
        set(value) {
            player.remainingFireTicks = value
        }
    override var isOnFire: Boolean
        get() = player.isOnFire
        set(value) {
            if (value) {
                player.setRemainingFireTicks(1)
            } else {
                player.clearFire()
            }
        }

    override var foodLevel: Int
        get() = player.foodData.foodLevel
        set(value) {
            player.foodData.foodLevel = value
        }
    override var saturationLevel: Float
        get() = player.foodData.saturationLevel
        set(value) {
            player.foodData.setSaturation(value)
        }
    override var exhaustion: Float
        get() = player.foodData.exhaustionLevel
        set(value) {
            player.foodData.setExhaustion(value)
        }

    override var experienceLevel: Int
        get() = player.experienceLevel
        set(value) { player.setExperienceLevels(value) }

    override var experienceProgress: Float
        get() = player.experienceProgress
        set(value) { player.setExperiencePoints((value * player.xpNeededForNextLevel).toInt()) }

    override val isSneaking: Boolean
        get() = player.isCrouching

    override fun sendMessage(message: IText) {
        player.sendSystemMessage(message.value)
    }

    override fun sendHotbarMessage(message: IText) {
        player.displayClientMessage(message.value, true)
    }

    override fun sendChatMessage(message: ISignedMessage, sender: IPlayerHandle) {
        require(message is SignedMessageImpl)
        require(sender is PlayerHandle)

        val bound = ChatType.bind(ChatType.CHAT, sender.player)
        player.sendChatMessage(OutgoingChatMessage.create(message.message), false, bound)
    }

    override fun sendTeamMessage(message: ISignedMessage, sender: IPlayerHandle, teamName: IText) {
        require(message is SignedMessageImpl)
        require(sender is PlayerHandle)

        val bound = ChatType.bind(
            if (sender.uuid == this.uuid) ChatType.TEAM_MSG_COMMAND_OUTGOING else ChatType.TEAM_MSG_COMMAND_INCOMING,
            sender.player
        ).withTargetName(teamName.value)
        player.sendChatMessage(OutgoingChatMessage.create(message.message), false, bound)
    }

    override fun sendTeamMessage(message: IText, sender: IPlayerHandle, teamName: IText) {
        require(sender is PlayerHandle)

        val bound = ChatType.bind(
            if (sender.uuid == this.uuid) ChatType.TEAM_MSG_COMMAND_OUTGOING else ChatType.TEAM_MSG_COMMAND_INCOMING,
            sender.player
        ).withTargetName(teamName.value)
        val decoratedMessage = bound.decorate(message.value)

        player.sendSystemMessage(decoratedMessage)
    }

    override fun sendTitle(title: IText, subtitle: IText?) {
        player.connection.send(ClientboundSetTitleTextPacket(title.value))
        player.connection.send(ClientboundSetSubtitleTextPacket(subtitle?.value ?: Component.empty()))
    }

    override fun hasPermissionLevel(level: Int): Boolean {
        return player.hasPermissions(level)
    }

    override fun canUseItem(stack: IItemStack): Boolean {
        val itemStack: ItemStack = stack.stack
        return when {
            itemStack.isEmpty -> false
            itemStack.useAnimation == UseAnim.EAT -> player.canEat(false)
            itemStack.useAnimation == UseAnim.DRINK -> true
            itemStack.useAnimation == UseAnim.BLOCK -> true // blocking with a shield
            itemStack.item is BoatItem -> true // trying to place a boat
            itemStack.item is BlockItem -> {
                // if holding a block, check if it can be placed on a surface within reach
                val hit = player.pick(5.0, 0f, false)
                hit.type == HitResult.Type.BLOCK
            }
            itemStack.item == Items.FIREWORK_ROCKET -> player.isFallFlying || player.isInWater
            itemStack.item == Items.SUSPICIOUS_STEW -> true
            itemStack.useOnRelease() -> true
            itemStack.useAnimation == UseAnim.NONE -> false
            else -> true
        }
    }

    override var gameMode: PlayerGameMode
        get() = player.gameMode.gameModeForPlayer.toGameMode()
        set(value) {
            player.setGameMode(value.toGameMode())
        }

    override val mainHandStack: IItemStack
        get() = getItemStackFactory().forStack(player.mainHandItem)

    override val offHandStack: IItemStack
        get() = getItemStackFactory().forStack(player.offhandItem)

    private fun allNestedStacks(stack: ItemStack): Sequence<Pair<ItemStack, ItemStack>> = sequence {
        stack[DataComponents.CONTAINER]?.nonEmptyItems()
            ?.forEach {
                yield(Pair(stack, it))
                yieldAll(allNestedStacks(it))
            }
        stack[DataComponents.BUNDLE_CONTENTS]?.items()
            ?.forEach {
                yield(Pair(stack, it))
                yieldAll(allNestedStacks(it))
            }
    }

    override fun allHeldStackViews(): Sequence<IContainerItemView> {
        val itemStackFactory = getItemStackFactory()
        return sequence {
            // yield all items in inventory
            for (i in 0 until player.inventory.containerSize) {
                yield(player.inventory.getItem(i))
            }

            // yield the current cursor stack
            yield(player.containerMenu.carried)

            // yield items held in crafting inputs
            player.containerMenu?.let { screenHandler ->
                if (screenHandler is InventoryMenu) {
                    yieldAll(screenHandler.craftSlots.items)
                }
            }

            // yield ender chest inventory
            yieldAll(player.enderChestInventory.items)
        }
            .filter { !it.isEmpty }
            .flatMap { sequenceOf(Pair(null, it)) + allNestedStacks(it) }
            .filter { !it.second.isEmpty }
            .map { (container, stack) ->
                val stackImpl = itemStackFactory.forStack(stack)
                if (container?.item is BundleItem) {
                    ContainerItemView.Bundle(container, stackImpl)
                } else if (container?.get(DataComponents.CONTAINER) != null) {
                    ContainerItemView.Container(container, stackImpl)
                } else {
                    ContainerItemView.Inventory(stackImpl)
                }
            }
    }

    override fun allInventorySlots(): Sequence<Pair<Int, IItemStack>> {
        val itemStackFactory = getItemStackFactory()
        return sequence {
            // yield all items in inventory
            for (i in 0 until player.inventory.containerSize) {
                yield(Pair(i, player.inventory.getItem(i)))
            }
        }.map { (i, stack) ->
            i to itemStackFactory.forStack(stack)
        }
    }

    override fun giveOrEquipStack(stack: IItemStack) {
        val itemStack = stack.stack

        // if the item can be equipped (e.g. iron chestplate), put it in an equipment slot
        val slot = Equipable.get(itemStack)?.equipmentSlot
        if (slot != null && player.canUseSlot(slot) && slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND) {
            player.setItemSlot(slot, itemStack)
        } else {
            // otherwise, put it in the player's inventory
            player.addItem(itemStack)
        }
    }

    override fun giveItemStack(stack: IItemStack) {
        player.addItem(stack.stack)
    }

    override fun removeStack(slot: Int) {
        player.inventory.removeItemNoUpdate(slot)
    }

    override fun setStack(slot: Int, stack: IItemStack) {
        if (slot in 0 until player.inventory.items.size) {
            player.getSlot(slot).set(stack.stack)
        } else {
            player.inventory.setItem(slot, stack.stack)
        }
    }

    override fun playSound(sound: PlayerSoundEvent, category: PlayerSoundCategory, volume: Float, pitch: Float) {
        player.playNotifySound(
            sound.toSoundEvent(),
            category.toSoundCategory(),
            volume,
            pitch,
        )
    }

    override val serverWorld: ServerLevel
        get() = player.serverLevel()

    override val blockPos: BlockPosition
        get() = BlockPosition(player.blockX, player.blockY, player.blockZ)

    override fun respawn(): IPlayerHandle {
        val networkHandler = player.connection
        networkHandler.handleClientCommand(ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN))
        return PlayerHandle(networkHandler.player, itemStackFactory)
    }

    override fun teleport(world: IServerWorld, pos: Vector3d, yaw: Float, pitch: Float) {
        val oldLevel = player.serverLevel()
        val newLevel = world.world
        val chunkTrackingOps = ServerLevelPlayerChunkTrackingOps(
            level = newLevel,
            player = player,
            chunkPos = ChunkPos(BlockPos.containing(pos.x, pos.y, pos.z)),
        )
        if (oldLevel !== newLevel) {
            preparePlayerChunkTracking(chunkTrackingOps)
        }
        player.teleportTo(world.world, pos.x, pos.y, pos.z, emptySet(), yaw, pitch)
        if (oldLevel !== newLevel) {
            finishPlayerChunkTracking(chunkTrackingOps)
        }
    }

    override fun forceTeleport(world: IServerWorld, pos: Vector3d, yaw: Float, pitch: Float) {
        val oldLevel = player.serverLevel()
        val newLevel = world.world
        val playerList = player.server.playerList
        val levelData = newLevel.levelData
        val chunkPos = ChunkPos(BlockPos.containing(pos.x, pos.y, pos.z))
        val chunkTrackingOps = ServerLevelPlayerChunkTrackingOps(newLevel, player, chunkPos)

        preparePlayerChunkTracking(chunkTrackingOps)
        player.setCamera(player)
        player.stopRiding()

        if (oldLevel !== newLevel) {
            player.connection.send(ClientboundRespawnPacket(player.createCommonSpawnInfo(newLevel), ClientboundRespawnPacket.KEEP_ALL_DATA))
            player.connection.send(ClientboundChangeDifficultyPacket(levelData.difficulty, levelData.isDifficultyLocked))
            playerList.sendPlayerPermissionLevel(player)

            oldLevel.removePlayerImmediately(player, Entity.RemovalReason.CHANGED_DIMENSION)
            player.revive()
            player.setServerLevel(newLevel)
            player.moveTo(pos.x, pos.y, pos.z, yaw, pitch)
            newLevel.addDuringTeleport(player)
            finishPlayerChunkTracking(chunkTrackingOps)
            player.connection.teleport(pos.x, pos.y, pos.z, yaw, pitch)
            player.connection.resetPosition()
        } else {
            player.connection.teleport(pos.x, pos.y, pos.z, yaw, pitch)
            player.connection.resetPosition()
            finishPlayerChunkTracking(chunkTrackingOps)
        }

        player.hasChangedDimension()
        player.connection.send(ClientboundPlayerAbilitiesPacket(player.abilities))
        playerList.sendLevelInfo(player, newLevel)
        playerList.sendAllPlayerInfo(player)
        playerList.sendActivePlayerEffects(player)
        net.neoforged.neoforge.attachment.AttachmentSync.syncInitialPlayerAttachments(player)
    }

    override fun setSpawnPoint(
        world: IServerWorld,
        spawn: BlockPosition,
        angle: Float,
        forced: Boolean,
        sendMessage: Boolean
    ) {
        val serverWorld: ServerLevel = world.world
        player.setRespawnPosition(serverWorld.dimension(), spawn.toBlockPos(), angle, forced, sendMessage)
    }

    override fun startRiding(entity: IEntity, force: Boolean) {
        player.startRiding(entity.entity, true)
    }

    override val abilities: IPlayerAbilities
        get() = object : IPlayerAbilities {
            override var allowFlying: Boolean
                get() = player.abilities.mayfly
                set(value) {
                    player.abilities.mayfly = value
                }
        }

    override fun sendAbilitiesUpdate() {
        player.onUpdateAbilities()
    }

    override fun syncInventory() {
        // Refresh the player's own inventory menu, then push a full container resync.
        // This flushes server-side inventory edits (e.g. items cleared on reset) to the client.
        player.inventoryMenu.sendAllDataToRemote()
        player.containerMenu.sendAllDataToRemote()
    }

    override fun resyncClientState(syncPosition: Boolean) {
        val level = player.serverLevel()
        val levelData = level.levelData
        val playerList = player.server.playerList

        if (syncPosition) {
            player.connection.teleport(player.x, player.y, player.z, player.yRot, player.xRot)
            player.connection.resetPosition()
        }
        player.connection.send(ClientboundChangeDifficultyPacket(levelData.difficulty, levelData.isDifficultyLocked))
        player.connection.send(
            ClientboundGameEventPacket(
                ClientboundGameEventPacket.CHANGE_GAME_MODE,
                player.gameMode.gameModeForPlayer.id.toFloat(),
            )
        )
        player.connection.send(ClientboundPlayerAbilitiesPacket(player.abilities))
        playerList.sendPlayerPermissionLevel(player)
        playerList.sendAllPlayerInfo(player)
        playerList.sendActivePlayerEffects(player)
        net.neoforged.neoforge.attachment.AttachmentSync.syncInitialPlayerAttachments(player)

        player.onUpdateAbilities()
        syncInventory()

        // Re-assert the invisibility flag so it is always re-sent below. The flag
        // lives in the shared-flags entity data; once it has settled to its default
        // (false), packDirty()/getNonDefaultValues() no longer include it, so a
        // client still showing a stale "invisible" (e.g. leftover countdown
        // invisibility whose removal packet was missed during the reset teleport)
        // would never be corrected. Setting it to its current value marks it dirty.
        player.setInvisible(player.isInvisible)

        val dataValues = player.entityData.packDirty() ?: player.entityData.nonDefaultValues
        if (dataValues != null) {
            val packet = ClientboundSetEntityDataPacket(player.id, dataValues)
            level.chunkSource.broadcastAndSend(player, packet)
        }
    }

    override fun refreshEntityTracking() {
        // Restore mutual visibility between all players after a bulk teleport
        // (post-game reset). Two distinct conditions must BOTH hold for vanilla
        // pairing to succeed, and earlier single-sided fixes each missed one:
        //
        //  1. Each player's TrackedEntity must be registered in ChunkMap.entityMap.
        //     That only happens via startTracking() once the destination section
        //     is accessible, which NeoForge promotes asynchronously a few ticks
        //     after the teleport. Callers MUST wait until areChunkEntitiesReady()
        //     before invoking this (see ResetService), or the iteration below
        //     finds no entities and pairs nothing.
        //  2. ChunkMap.TrackedEntity.updatePlayer() gates pairing on
        //     isChunkTracked(), which is false while the target chunk is still
        //     pending in the client's chunk sender — true for EVERY player right
        //     after a simultaneous reset teleport. So the vanilla move() path
        //     silently pairs nothing until each client ACKs its chunks (which is
        //     what "players appear only after they move around" really was).
        //
        // move() handles chunk-level housekeeping; the force-pair (our mixin)
        // then establishes the entity pairings directly, bypassing the
        // isChunkTracked gate. Once paired, nothing re-evaluates a stationary
        // player, so the pairing sticks.
        val level = player.serverLevel()
        val chunkMap = level.chunkSource.chunkMap
        chunkMap.move(player)
        (chunkMap as ChunkMapForceTracking).exbingo_forceAllPlayerPairings(level.players())
    }

    override fun isEntityTrackingReady(): Boolean {
        val chunkMap = player.serverLevel().chunkSource.chunkMap as ChunkMapForceTracking
        return chunkMap.exbingo_isPlayerTrackingReady(player)
    }
}

private fun PlayerSoundEvent.toSoundEvent() = when (this) {
    PlayerSoundEvent.BLOCK_LEVER_CLICK -> SoundEvents.LEVER_CLICK
    PlayerSoundEvent.BLOCK_WOODEN_BUTTON_CLICK_ON -> SoundEvents.WOODEN_BUTTON_CLICK_ON
    PlayerSoundEvent.BLOCK_WOODEN_BUTTON_CLICK_OFF -> SoundEvents.WOODEN_BUTTON_CLICK_OFF
    PlayerSoundEvent.ENTITY_PLAYER_LEVELUP -> SoundEvents.PLAYER_LEVELUP
    PlayerSoundEvent.ENTITY_SHULKER_AMBIENT -> SoundEvents.SHULKER_AMBIENT
    PlayerSoundEvent.ENTITY_TNT_PRIMED -> SoundEvents.TNT_PRIMED
    PlayerSoundEvent.BLOCK_NOTE_BLOCK_BASS -> SoundEvents.NOTE_BLOCK_BASS.value()
    PlayerSoundEvent.ITEM_LODESTONE_COMPASS_LOCK -> SoundEvents.LODESTONE_COMPASS_LOCK
    PlayerSoundEvent.BLOCK_PORTAL_TRAVEL -> SoundEvents.PORTAL_TRAVEL
    PlayerSoundEvent.BLOCK_NOTE_BLOCK_PLING -> SoundEvents.NOTE_BLOCK_PLING.value()
}

private fun PlayerSoundCategory.toSoundCategory() = when (this) {
    PlayerSoundCategory.MAIN -> SoundSource.MASTER
    PlayerSoundCategory.RECORDS -> SoundSource.RECORDS
    PlayerSoundCategory.BLOCKS -> SoundSource.BLOCKS
}

private fun GameType.toGameMode() = when (this) {
    GameType.SURVIVAL -> PlayerGameMode.SURVIVAL
    GameType.CREATIVE -> PlayerGameMode.CREATIVE
    GameType.ADVENTURE -> PlayerGameMode.ADVENTURE
    GameType.SPECTATOR -> PlayerGameMode.SPECTATOR
}

private fun PlayerGameMode.toGameMode() = when (this) {
    PlayerGameMode.SURVIVAL -> GameType.SURVIVAL
    PlayerGameMode.CREATIVE -> GameType.CREATIVE
    PlayerGameMode.ADVENTURE -> GameType.ADVENTURE
    PlayerGameMode.SPECTATOR -> GameType.SPECTATOR
}
