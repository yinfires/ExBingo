package me.jfenn.bingo.impl

import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.ITeamHandle
import me.jfenn.bingo.platform.ITeamManager
import net.minecraft.world.scores.PlayerTeam
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.ChatFormatting

class TeamManager(
    private val server: MinecraftServer,
) : ITeamManager {

    override fun listTeams(): List<String> {
        return server.scoreboard.teamNames.toList()
    }

    override fun getTeam(id: String): ITeamHandle? {
        return server.scoreboard.getPlayerTeam(id)
            ?.let { TeamHandle(it) }
    }

    override fun createTeam(teamName: String, displayName: IText, color: ChatFormatting): ITeamHandle {
        return TeamHandle(
            server.scoreboard.getPlayerTeam(teamName)
                ?: server.scoreboard.addPlayerTeam(teamName)
        ).also {
            it.displayName = displayName
            it.color = color
        }
    }

    override fun deleteTeam(teamName: String) {
        val team = server.scoreboard.getPlayerTeam(teamName)
        if (team != null) server.scoreboard.removePlayerTeam(team)
    }

    override fun getPlayerTeam(player: ServerPlayer): TeamHandle? {
        return player.server.scoreboard.getPlayersTeam(player.scoreboardName)
            ?.let { TeamHandle(it) }
    }

    override fun setPlayerTeam(player: ServerPlayer, team: ITeamHandle?) {
        require(team is TeamHandle?)

        if (team != null) {
            player.server.scoreboard.addPlayerToTeam(player.scoreboardName, team.team)
        } else {
            // leave the player's current team (set to null)
            val currentTeam = getPlayerTeam(player) ?: return
            player.server.scoreboard.removePlayerFromTeam(player.scoreboardName, currentTeam.team)
        }
    }
}

class TeamHandle(
    val team: PlayerTeam
) : ITeamHandle {
    override val name: String
        get() = team.name

    override var displayName: IText
        get() = TextImpl(team.displayName.copy())
        set(value) { team.setDisplayName(value.value) }

    override var color: ChatFormatting
        get() = team.color
        set(value) { team.color = value }
}
