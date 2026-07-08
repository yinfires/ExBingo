package me.jfenn.bingo.common.card.tierlist

import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.common.utils.formatTitle
import me.jfenn.bingo.generated.StringKey
import net.minecraft.ChatFormatting

enum class TierLabel(
    val formatting: ChatFormatting,
    val string: StringKey,
    val shortString: StringKey,
) {
    S(ChatFormatting.DARK_RED, StringKey.TierS, StringKey.TierSShort),
    A(ChatFormatting.RED, StringKey.TierA, StringKey.TierAShort),
    B(ChatFormatting.GOLD, StringKey.TierB, StringKey.TierBShort),
    C(ChatFormatting.YELLOW, StringKey.TierC, StringKey.TierCShort),
    D(ChatFormatting.GREEN, StringKey.TierD, StringKey.TierDShort);

    fun text(text: TextProvider) = text.string(this.string).formatted(formatting)

    companion object {
        const val LIST_EASY = "easy"
        val DIFFICULTY_EASY = listOf(0, 0, 0, 9, 16)
        val DIFFICULTY_PRESETS = linkedMapOf(
            LIST_EASY to DIFFICULTY_EASY,
            "medium" to listOf(0, 0, 3, 10, 12),
            "hard" to listOf(0, 1, 5, 10, 9),
            "extreme" to listOf(0, 5, 7, 7, 6),
            "impossible" to listOf(5, 7, 6, 5, 2),
        )
        private val LEGACY_ALPHABETICAL_DIFFICULTY_PRESET_KEYS = DIFFICULTY_PRESETS.keys.sorted()

        fun normalizeDefaultDifficultyPresetOrder(presets: Map<String, List<Int>>): Map<String, List<Int>> {
            val isLegacyAlphabeticalDefault = presets.keys.toList() == LEGACY_ALPHABETICAL_DIFFICULTY_PRESET_KEYS &&
                    presets.size == DIFFICULTY_PRESETS.size &&
                    presets.all { (name, distribution) -> DIFFICULTY_PRESETS[name] == distribution }
            return if (isLegacyAlphabeticalDefault) DIFFICULTY_PRESETS else presets
        }

        fun presetText(text: TextProvider, difficulty: String) = text.translatable("bingo.card_difficulty.$difficulty", difficulty.formatTitle())
    }
}
