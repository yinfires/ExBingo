package me.jfenn.bingo.common.options

import me.jfenn.bingo.common.LOBBY_WORLD_ID
import me.jfenn.bingo.platform.IServerWorldFactory

private val EXCLUDED_SPAWN_DIMENSIONS = setOf(
    // Iron's Spells 'n Spellbooks stores its temporary pocket-room spell world here.
    "irons_spellbooks:pocket_dimension",
    // Ice and Fire exposes a dread dimension, but this pack does not treat it as a playable spawn world.
    "iceandfire:dread_land",
)

internal fun IServerWorldFactory.listSelectableSpawnDimensions(): List<String> {
    return listWorlds()
        .map { it.identifier }
        .filterNot { it == LOBBY_WORLD_ID.toString() || it in EXCLUDED_SPAWN_DIMENSIONS }
        .sorted()
}

internal fun IServerWorldFactory.coerceSpawnDimension(dimension: String): String {
    val selectable = listSelectableSpawnDimensions()
    return dimension.takeIf { it in selectable } ?: "minecraft:overworld"
}
