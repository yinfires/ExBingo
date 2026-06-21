package me.jfenn.bingo.impl

import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.scoreboard.IObjectiveHandle
import me.jfenn.bingo.platform.scoreboard.IScoreboardManager
import me.jfenn.bingo.platform.scoreboard.ScoreChange
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket
import net.minecraft.network.protocol.game.ClientboundResetScorePacket
import net.minecraft.network.protocol.game.ClientboundSetScorePacket
import net.minecraft.world.scores.*
import net.minecraft.world.scores.criteria.ObjectiveCriteria
import net.minecraft.network.chat.numbers.BlankFormat
import net.minecraft.server.MinecraftServer
import net.minecraft.server.ServerScoreboard
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import java.util.*

class ScoreboardManager(
    private val server: MinecraftServer,
) : IScoreboardManager {

    override fun createDummyObjective(name: String): IObjectiveHandle {
        val scoreboard: ServerScoreboard = server.scoreboard
        val objective = scoreboard.getObjective(name) ?: run {
            scoreboard.addObjective(
                name,
                ObjectiveCriteria.DUMMY,
                Component.empty(),
                ObjectiveCriteria.RenderType.INTEGER,
                false,
                BlankFormat.INSTANCE,
            )
        }

        return ObjectiveHandle(scoreboard, objective)
    }

    override fun removeObjective(handle: IObjectiveHandle) {
        require(handle is ObjectiveHandle)
        val scoreboard: ServerScoreboard = server.scoreboard
        scoreboard.removeObjective(handle.objective)
    }

    override fun setScoreboardText(handle: IObjectiveHandle, textLines: List<ScoreChange.Create>) {
        require(handle is ObjectiveHandle)

        // Remove any previous sidebar lines that have been changed
        for (score in server.scoreboard.listPlayerScores(handle.objective)) {
            if (textLines.none { it.name == score.owner() }) {
                server.scoreboard.resetSinglePlayerScore(ScoreHolder.forNameOnly(score.owner()), handle.objective)
            }
        }

        textLines.forEachIndexed { i, text ->
            val score = server.scoreboard.getOrCreatePlayerScore(
                object : ScoreHolder {
                    override fun getScoreboardName(): String = text.name
                    override fun getDisplayName(): Component = text.text
                },
                handle.objective,
                true
            )
            score.set(textLines.size - i - 1)
        }
    }

    override fun sendObjectiveCreate(player: IPlayerHandle, handle: IObjectiveHandle) {
        require(handle is ObjectiveHandle)
        val serverPlayer: ServerPlayer = player.player

        serverPlayer.connection.send(
            ClientboundSetObjectivePacket(
                handle.objective,
                ClientboundSetObjectivePacket.METHOD_ADD,
            )
        )

        serverPlayer.connection.send(
            ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, handle.objective)
        )
    }

    override fun sendObjectiveDelete(player: IPlayerHandle, handle: IObjectiveHandle) {
        require(handle is ObjectiveHandle)
        val serverPlayer: ServerPlayer = player.player

        serverPlayer.connection.send(
            ClientboundSetObjectivePacket(
                handle.objective,
                ClientboundSetObjectivePacket.METHOD_REMOVE,
            )
        )
    }

    override fun sendObjectiveDisplayUpdate(player: IPlayerHandle, handle: IObjectiveHandle) {
        require(handle is ObjectiveHandle)
        val serverPlayer: ServerPlayer = player.player

        serverPlayer.connection.send(
            ClientboundSetObjectivePacket(
                handle.objective,
                ClientboundSetObjectivePacket.METHOD_CHANGE,
            )
        )
    }

    override fun sendScoreChanges(player: IPlayerHandle, handle: IObjectiveHandle, changes: List<ScoreChange>) {
        require(handle is ObjectiveHandle)
        val serverPlayer: ServerPlayer = player.player

        for (change in changes) {
            when (change) {
                is ScoreChange.Create -> {
                    serverPlayer.connection.send(ClientboundSetScorePacket(change.name, handle.name, change.value, Optional.of(change.text), Optional.empty()))
                }
                is ScoreChange.Update -> {
                    serverPlayer.connection.send(ClientboundSetScorePacket(change.name, handle.name, change.value, Optional.of(change.text), Optional.empty()))
                }
                is ScoreChange.Remove -> {
                    serverPlayer.connection.send(ClientboundResetScorePacket(change.name, handle.name))
                }
            }
        }
    }

    override fun getPlayerName(player: IPlayerHandle): String {
        val serverPlayer: ServerPlayer = player.player
        return serverPlayer.scoreboardName
    }

    override fun getByName(name: String): IObjectiveHandle? {
        val scoreboard: ServerScoreboard = server.scoreboard
        val objective = scoreboard.getObjective(name) ?: return null
        return ObjectiveHandle(scoreboard, objective)
    }
}

class ObjectiveHandle(
    private val scoreboard: ServerScoreboard,
    val objective: Objective,
) : IObjectiveHandle {
    override val name: String
        get() = objective.name

    override var displayName: IText
        get() = TextImpl(objective.displayName.copy())
        set(value) { objective.displayName = value.value }

    override fun getForPlayer(player: IPlayerHandle): Int? {
        val playerImpl = player as PlayerHandle
        return scoreboard.getPlayerScoreInfo(playerImpl.player, objective)?.value()
    }

    override fun setPlayer(player: IPlayerHandle, value: Int) {
        val playerImpl = player as PlayerHandle
        scoreboard.getOrCreatePlayerScore(playerImpl.player, objective).apply {
            set(value)
        }
    }
}
