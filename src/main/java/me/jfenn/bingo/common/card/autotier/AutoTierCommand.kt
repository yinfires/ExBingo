package me.jfenn.bingo.common.card.autotier

import me.jfenn.bingo.common.Permission
import me.jfenn.bingo.common.commands.hasPermission
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.commands.ICommandManager
import net.minecraft.ChatFormatting

/**
 * `/bingo autotier ...` - OP-only commands that auto-classify uncategorized, non-vanilla
 * objectives and write the result directly into the server-side config tier list.
 *
 *  - `/bingo autotier generate` - score & assign newly-uncategorized modded objectives
 *  - `/bingo autotier clear`    - remove the generated auto-tier list
 */
class AutoTierCommand(
    commandManager: ICommandManager,
    private val text: TextProvider,
) {
    init {
        commandManager.register("bingo") {
            literal("autotier") {
                requires {
                    hasPermission(Permission.COMMAND_AUTOTIER)
                }

                literal("generate") {
                    executes {
                        if (!hasPermission(Permission.COMMAND_AUTOTIER))
                            error(text.string(StringKey.CommandAutotierNoPermission))

                        sendFeedback(
                            text.string(StringKey.CommandAutotierGenerating)
                                .formatted(ChatFormatting.GRAY)
                        )

                        val result = scope.get<AutoTierService>().generateAndReload()

                        sendFeedback(
                            text.string(
                                StringKey.CommandAutotierGenerateSuccess,
                                result.assigned.toString(),
                                result.skipped.toString(),
                            ).formatted(ChatFormatting.GREEN)
                        )
                    }
                }

                literal("clear") {
                    executes {
                        if (!hasPermission(Permission.COMMAND_AUTOTIER))
                            error(text.string(StringKey.CommandAutotierNoPermission))

                        scope.get<AutoTierService>().clearAndReload()
                        sendFeedback(
                            text.string(StringKey.CommandAutotierClearSuccess)
                                .formatted(ChatFormatting.YELLOW)
                        )
                    }
                }
            }
        }
    }
}
