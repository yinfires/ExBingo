package me.jfenn.bingo.common.teamchest

import me.jfenn.bingo.common.commands.canConfigureGame
import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.config.ConfigService
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.team.TeamService
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.commands.CommandBuilder
import me.jfenn.bingo.platform.commands.ICommandManager
import me.jfenn.bingo.platform.commands.IExecutionContext
import net.minecraft.ChatFormatting

internal class TeamTeleportCommand(
    commandManager: ICommandManager,
    private val config: BingoConfig,
    private val configService: ConfigService,
    private val text: TextProvider,
) : BingoComponent() {

    private fun IExecutionContext.requireConfigEditable(): Boolean {
        val state = scope.get<BingoState>()
        if (state.state == GameState.PREGAME)
            return true

        sendMessage(text.string(StringKey.CommandTeamFeatureConfigLocked).formatted(ChatFormatting.RED))
        return false
    }

    private fun IExecutionContext.toggleTeamTeleport() {
        if (!requireConfigEditable()) return

        config.teamTeleportEnabled = !config.teamTeleportEnabled
        configService.writeConfig(config)
        sendFeedback(text.string(StringKey.CommandTeamTeleportToggle, text.boolean(config.teamTeleportEnabled)))
    }

    private fun IExecutionContext.teleportToTeamPlayer(target: IPlayerHandle) {
        val sender = playerOrThrow
        val state = scope.get<BingoState>()
        val teamService = scope.get<TeamService>()

        if (state.state != GameState.PLAYING) {
            sendMessage(text.string(StringKey.CommandTeamTeleportGameNotStarted).formatted(ChatFormatting.RED))
            return
        }

        if (!config.teamTeleportEnabled) {
            sendMessage(text.string(StringKey.CommandTeamTeleportDisabled).formatted(ChatFormatting.RED))
            return
        }

        if (sender.uuid == target.uuid) {
            sendMessage(text.string(StringKey.CommandTeamTeleportSelf).formatted(ChatFormatting.RED))
            return
        }

        val senderTeam = teamService.getPlayerTeam(sender)
        if (senderTeam == null) {
            sendMessage(text.string(StringKey.CommandTeamTeleportNoTeam).formatted(ChatFormatting.RED))
            return
        }

        val targetTeam = teamService.getPlayerTeam(target)
        if (targetTeam?.key != senderTeam.key) {
            sendMessage(text.string(StringKey.CommandTeamTeleportNotSameTeam, target.playerName).formatted(ChatFormatting.RED))
            return
        }

        sender.forceTeleport(target.world, target.pos, target.yaw, target.pitch)
        sendFeedback(text.string(StringKey.CommandTeamTeleportSuccess, target.playerName).formatted(ChatFormatting.GREEN))
        target.sendMessage(text.string(StringKey.CommandTeamTeleportNotice, sender.playerName).formatted(ChatFormatting.GREEN))
    }

    private fun CommandBuilder.teamTeleportRoot(command: String) {
        executes {
            sendMessage(text.string(StringKey.CommandTeamTeleportUsage, "/$command <player>").formatted(ChatFormatting.RED))
        }

        player("target") { targetArg ->
            executes { teleportToTeamPlayer(getArgument(targetArg)) }
        }
    }

    init {
        commandManager.register("teamtp") { teamTeleportRoot("teamtp") }
        commandManager.register("ttp") { teamTeleportRoot("ttp") }
        commandManager.register("tptoggle") {
            requires { canConfigureGame() }
            executes { toggleTeamTeleport() }
        }
    }
}
