package me.jfenn.bingo.common.controller

import me.jfenn.bingo.common.Permission
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.spectator.SpectatorInventoryScreenHandler
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.team.TeamService
import me.jfenn.bingo.integrations.permissions.IPermissionsApi
import me.jfenn.bingo.platform.block.BlockPosition
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.model.ActionResult
import me.jfenn.bingo.platform.event.model.AttackEntityEvent
import me.jfenn.bingo.platform.event.model.UseBlockEvent
import me.jfenn.bingo.platform.event.model.UseEntityEvent
import net.minecraft.world.level.block.*
import net.minecraft.world.entity.vehicle.ChestBoat
import net.minecraft.world.entity.vehicle.MinecartChest
import net.minecraft.world.CompoundContainer
import net.minecraft.world.Container
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.TrapDoorBlock

internal class PlayerSpectatorController(
    eventBus: IEventBus,
    private val state: BingoState,
    private val teamService: TeamService,
    private val permissions: IPermissionsApi,
) : BingoComponent() {

    init {
        eventBus.register(UseBlockEvent) {
            if (!state.isLobbyMode) return@register null
            val player = it.player
            if (state.state != GameState.PLAYING) return@register null
            if (teamService.isPlaying(player)) return@register null
            if (player.player.gameMode.gameModeForPlayer != GameType.ADVENTURE) return@register null

            // if the player is spectating a game, do not
            // allow them to modify any inventory

            val blockState = player.world.world.getBlockState(it.hit.blockPos)
            val block = blockState.block

            // if the player has permission to open doors...
            if (permissions.hasPermission(player, Permission.SPECTATOR_USE_DOORS)) {
                when (block) {
                    is DoorBlock -> return@register null
                    is TrapDoorBlock -> return@register null
                }
            }

            // if the interacted block has an inventory, open it as read-only...
            var rows = 0
            val inventory: Container? = when (block) {
                is ChestBlock -> { // includes TrappedChestBlock and double-chests
                    val inventory = ChestBlock.getContainer(block, blockState, player.world.world, it.hit.blockPos, true)
                    rows = if (inventory is CompoundContainer) 6 else 3
                    inventory
                }
                is BarrelBlock -> {
                    rows = 3
                    player.world.world.getBlockEntity(it.hit.blockPos) as? Container
                }
                is ShulkerBoxBlock -> {
                    rows = 3
                    player.world.world.getBlockEntity(it.hit.blockPos) as? Container
                }
                else -> null
            }

            if (
                permissions.hasPermission(player, Permission.SPECTATOR_VIEW_INVENTORY)
                && inventory != null
            ) {
                player.player.openMenu(
                    SimpleMenuProvider(
                        { syncId, inv, _ ->
                            SpectatorInventoryScreenHandler(
                                type = when (rows) {
                                    6 -> MenuType.GENERIC_9x6
                                    else -> MenuType.GENERIC_9x3
                                },
                                syncId = syncId,
                                rows = rows,
                                playerInventory = inv,
                                inventory = inventory,
                            )
                        },
                        block.name,
                    )
                )
            }

            ActionResult.Fail(Unit)
        }

        eventBus.register(UseEntityEvent) {
            if (!state.isLobbyMode) return@register null
            val player = it.player
            if (state.state != GameState.PLAYING) return@register null
            if (teamService.isPlaying(player)) return@register null
            if (player.player.gameMode.gameModeForPlayer != GameType.ADVENTURE) return@register null

            // if the player is spectating a game, do not
            // allow them to modify any inventory

            // if the interacted entity has an inventory, open it as read-only...
            val inventory: Container? = when (
                val entity = it.entity
            ) {
                is MinecartChest -> entity
                is ChestBoat -> entity
                else -> null
            }

            if (
                permissions.hasPermission(player, Permission.SPECTATOR_VIEW_INVENTORY)
                && inventory != null
            ) {
                player.player.openMenu(
                    SimpleMenuProvider(
                        { syncId, inv, _ ->
                            SpectatorInventoryScreenHandler(
                                type = MenuType.GENERIC_9x3,
                                syncId = syncId,
                                rows = 3,
                                playerInventory = inv,
                                inventory = inventory,
                            )
                        },
                        it.entity.name,
                    )
                )
            }

            ActionResult.Fail(Unit)
        }

        eventBus.register(AttackEntityEvent) {
            if (!state.isLobbyMode) return@register null
            val player = it.player
            if (state.state != GameState.PLAYING) return@register null
            if (teamService.isPlaying(player)) return@register null
            if (player.player.gameMode.gameModeForPlayer != GameType.ADVENTURE) return@register null

            // if the player is spectating a game, do not
            // allow them to attack any entity

            ActionResult.Fail(Unit)
        }
    }

}
