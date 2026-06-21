package me.jfenn.bingo.common.data

import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.card.filter.ObjectiveFilterPreset
import me.jfenn.bingo.common.card.filter.ObjectiveFilterPresets
import me.jfenn.bingo.common.utils.decodeFromUtf8Stream
import me.jfenn.bingo.platform.IJsonSerializers
import net.minecraft.server.packs.resources.ResourceManager
import org.slf4j.Logger

class FilterPresetsLoader(
    private val serializers: IJsonSerializers,
    private val log: Logger,
) {
    fun loadFilterPresets(
        manager: ResourceManager
    ): ObjectiveFilterPresets {
        val newPresets = mutableListOf<ObjectiveFilterPreset>()
        val presets = manager.listResourceStacks(MOD_ID_BINGO) {
            it.path.substringAfter('/') == "filters.json"
        }

        val json = serializers.jsonStrict

        for ((id, files) in presets) {
            for (file in files) {
                val data = try {
                    file.open().use {
                        json.decodeFromUtf8Stream<ObjectiveFilterPresets>(it)
                    }
                } catch (e: Throwable) {
                    log.error("[FilterPresetsLoader] Error reading filter presets $id: ${e.message}")
                    log.debug("Error reading filters.json {}", id, e)
                    continue
                }

                newPresets += data.filters
            }

        }

        return ObjectiveFilterPresets(newPresets)
    }
}
