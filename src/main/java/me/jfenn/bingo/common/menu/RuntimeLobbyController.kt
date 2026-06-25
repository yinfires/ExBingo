package me.jfenn.bingo.common.menu

internal interface RuntimeLobbyController {
    fun prepareLobbyFiles()
    fun suspendPregameSpawn()
    fun spawnLobby()
    fun menuEntityStats(): MenuEntityStats
}
