package me.jfenn.bingo.common.card.filter

import me.jfenn.bingo.common.Permission
import me.jfenn.bingo.common.commands.hasPermission
import me.jfenn.bingo.common.config.ConfigService
import me.jfenn.bingo.common.event.model.OptionsChangedEvent
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.commands.ICommandManager
import me.jfenn.bingo.platform.commands.IExecutionContext
import me.jfenn.bingo.platform.event.IEventBus
import net.minecraft.ChatFormatting

/**
 * `/bingo carddisable ...` & `/bingo cardenable ...` - OP-only commands that toggle whether a
 * filter preset ("board") is available. Disabled boards are hidden from the board-selection
 * menu and excluded from every preset enumeration (so they can't be picked individually or via
 * any "select all"). The disabled set is written directly into the server-side config.json, so
 * it persists across restarts and applies immediately (no /reload needed).
 *
 *  - `/bingo carddisable <board>` - hide a board
 *  - `/bingo carddisable list`    - list currently disabled boards
 *  - `/bingo cardenable <board>`  - re-enable a previously disabled board
 */
class CardToggleCommand(
    commandManager: ICommandManager,
    private val text: TextProvider,
    private val eventBus: IEventBus,
) {
    /** ids of every preset, including ones already disabled (which are hidden elsewhere). */
    private fun IExecutionContext.listAllPresetIds(): List<String> =
        scope.get<ObjectiveFilterService>().getAllPresetFilters().keys.toList()

    private fun IExecutionContext.disabledIds(): Set<String> =
        scope.get<ConfigService>().config.disabledFilterPresets

    private fun IExecutionContext.setDisabled(disabled: Set<String>) {
        val configService = scope.get<ConfigService>()
        // reassigning the delegated property persists it to config/exbingo/config.json
        configService.config = configService.config.copy(disabledFilterPresets = disabled)
        // refresh the lobby board-selection menu so the change shows without a lobby reset
        eventBus.emit(OptionsChangedEvent, Unit)
    }

    init {
        commandManager.register("bingo") {
            literal("carddisable") {
                requires { hasPermission(Permission.COMMAND_CARD_TOGGLE) }

                literal("list") {
                    executes {
                        if (!hasPermission(Permission.COMMAND_CARD_TOGGLE))
                            error(text.string(StringKey.CommandCardToggleNoPermission))

                        val disabled = disabledIds()
                        if (disabled.isEmpty()) {
                            sendFeedback(
                                text.string(StringKey.CommandCardToggleListEmpty)
                                    .formatted(ChatFormatting.GRAY)
                            )
                        } else {
                            sendFeedback(
                                text.string(StringKey.CommandCardToggleListHeader, disabled.size.toString())
                                    .formatted(ChatFormatting.YELLOW)
                            )
                            for (id in disabled.sorted()) {
                                sendFeedback(text.literal("  - $id").formatted(ChatFormatting.GRAY))
                            }
                        }
                    }
                }

                string("board", { listAllPresetIds().minus(disabledIds()) }) { boardArg ->
                    executes {
                        if (!hasPermission(Permission.COMMAND_CARD_TOGGLE))
                            error(text.string(StringKey.CommandCardToggleNoPermission))

                        val board = getArgument(boardArg)
                        if (!listAllPresetIds().contains(board))
                            error(text.string(StringKey.CommandCardToggleUnknown, board))

                        val disabled = disabledIds()
                        if (disabled.contains(board)) {
                            sendFeedback(
                                text.string(StringKey.CommandCardToggleAlreadyDisabled, board)
                                    .formatted(ChatFormatting.GRAY)
                            )
                        } else {
                            setDisabled(disabled + board)
                            sendFeedback(
                                text.string(StringKey.CommandCardToggleDisabled, board)
                                    .formatted(ChatFormatting.YELLOW)
                            )
                        }
                    }
                }
            }

            literal("cardenable") {
                requires { hasPermission(Permission.COMMAND_CARD_TOGGLE) }

                string("board", { disabledIds().toList() }) { boardArg ->
                    executes {
                        if (!hasPermission(Permission.COMMAND_CARD_TOGGLE))
                            error(text.string(StringKey.CommandCardToggleNoPermission))

                        val board = getArgument(boardArg)
                        val disabled = disabledIds()
                        if (!disabled.contains(board)) {
                            sendFeedback(
                                text.string(StringKey.CommandCardToggleNotDisabled, board)
                                    .formatted(ChatFormatting.GRAY)
                            )
                        } else {
                            setDisabled(disabled - board)
                            sendFeedback(
                                text.string(StringKey.CommandCardToggleEnabled, board)
                                    .formatted(ChatFormatting.GREEN)
                            )
                        }
                    }
                }
            }
        }
    }
}
