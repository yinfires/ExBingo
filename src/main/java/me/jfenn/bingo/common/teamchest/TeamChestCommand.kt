package me.jfenn.bingo.common.teamchest

import me.jfenn.bingo.common.commands.canConfigureGame
import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.config.ConfigService
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.commands.CommandBuilder
import me.jfenn.bingo.platform.commands.ICommandManager
import me.jfenn.bingo.platform.commands.IExecutionContext
import net.minecraft.ChatFormatting
import java.util.UUID

internal class TeamChestCommand(
    commandManager: ICommandManager,
    private val config: BingoConfig,
    private val configService: ConfigService,
    private val text: TextProvider,
) : BingoComponent() {

    private fun IExecutionContext.openTeamChest() {
        if (scope.get<SpectatorTeamJoinService>().openTeamSelection(playerOrThrow)) {
            return
        }

        scope.get<TeamChestService>().openTeamChest(playerOrThrow)
    }

    private fun IExecutionContext.requireConfigEditable(): Boolean {
        val state = scope.get<BingoState>()
        if (state.state == GameState.PREGAME)
            return true

        sendMessage(text.string(StringKey.CommandTeamFeatureConfigLocked).formatted(ChatFormatting.RED))
        return false
    }

    private fun IExecutionContext.toggleTeamChest() {
        if (!requireConfigEditable()) return

        config.teamChestEnabled = !config.teamChestEnabled
        configService.writeConfig(config)
        sendFeedback(text.string(StringKey.CommandTeamChestToggle, text.boolean(config.teamChestEnabled)))
    }

    private fun IExecutionContext.toggleTeamChestScoring() {
        if (!requireConfigEditable()) return

        config.teamChestCountsForObjectives = !config.teamChestCountsForObjectives
        configService.writeConfig(config)
        sendFeedback(text.string(StringKey.CommandTeamChestCountToggle, text.boolean(config.teamChestCountsForObjectives)))
    }

    private fun CommandBuilder.teamChestRoot() {
        executes { openTeamChest() }

        literal("toggle") {
            requires { canConfigureGame() }
            executes { toggleTeamChest() }
        }

        literal("count") {
            requires { canConfigureGame() }
            executes { toggleTeamChestScoring() }
        }
    }

    init {
        commandManager.register("teamchest") { teamChestRoot() }
        commandManager.register("tc") { teamChestRoot() }
        commandManager.register("bingo") {
            literal("teamrequest") {
                literal("accept") {
                    string("requester") { requesterArg ->
                        string("team") { teamArg ->
                            executes {
                                scope.get<SpectatorTeamJoinService>().approve(
                                    approver = playerOrThrow,
                                    requesterId = UUID.fromString(getArgument(requesterArg)),
                                    teamKey = BingoTeamKey(getArgument(teamArg)),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
