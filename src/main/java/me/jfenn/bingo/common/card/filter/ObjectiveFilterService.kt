package me.jfenn.bingo.common.card.filter

import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.common.config.ConfigService
import me.jfenn.bingo.common.data.ScopedData
import me.jfenn.bingo.common.text.TextProvider
import net.minecraft.ChatFormatting

internal class ObjectiveFilterService(
    private val configService: ConfigService,
    private val data: ScopedData,
    private val text: TextProvider,
) {
    fun getPresetFilters(): Map<String, ObjectiveFilterPreset> {
        // read the config live (not a cached BingoConfig instance) so that toggling a board
        // via /bingo carddisable takes effect immediately, without a /reload or restart.
        val disabled = configService.config.disabledFilterPresets
        return getAllPresetFilters()
            .filterKeys { it !in disabled }
    }

    /**
     * Like [getPresetFilters] but includes presets that ops have disabled. Used by the
     * board-management commands to enable/list disabled boards (which are otherwise hidden).
     */
    fun getAllPresetFilters(): Map<String, ObjectiveFilterPreset> {
        val configPresets = configService.config.itemFilterPresets
            .map { (id, value) -> ObjectiveFilterPreset(id, null, value) }
            .associateBy { it.id }

        val dataPresets = data.filterPresets.filters
            .associateBy { it.id }

        return configPresets + dataPresets
    }

    fun formatFilter(itemFilter: ObjectiveFilterList, includePresetDetails: Boolean): IText {
        return getPresetFilters().values
            .find { filter -> filter.value == itemFilter }
            ?.let { filter ->
                val name = filter.name(text)
                when {
                    includePresetDetails -> name.append(" ")
                        .append(text.literal("(${filter.value})").formatted(ChatFormatting.GRAY))
                    else -> name
                }
            }
            ?: text.literal(itemFilter.toString())
    }
}