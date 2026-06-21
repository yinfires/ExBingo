package me.jfenn.bingo.common.team

import kotlinx.serialization.Serializable
import me.jfenn.bingo.common.utils.FormattingSerializer
import me.jfenn.bingo.platform.text.ITextSerialized
import net.minecraft.ChatFormatting

@Serializable
class BingoTeamPreset(
    val name: ITextSerialized,
    val shouldFormatName: Boolean = false,
    val symbol: String? = null,
    @Serializable(with = FormattingSerializer::class)
    val color: ChatFormatting = ChatFormatting.RESET,
    val blockId: String? = null,
)
