package me.jfenn.bingo.client.common.hud

import kotlinx.serialization.Serializable
import me.jfenn.bingo.common.map.Color
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.common.utils.ColorType
import net.minecraft.ChatFormatting
import net.minecraft.resources.ResourceLocation
import kotlin.reflect.KProperty1

@Serializable
internal data class BingoCardColors(
    val default: Team = Team(),
    val teams: Map<String, Team> = emptyMap(),
) {

    companion object {
        val DEFAULTS = TeamColors()
    }

    @Serializable
    data class Team(
        val tileAchievedColor: ColorType? = null,
        val tileFlashingColor: ColorType? = null,
        val tileProgressColor: ColorType? = null,
        val textX: Int? = null,
        val textY: Int? = null,
        val textColor: ColorType? = null,
        val outlineColor: ColorType? = null,
        val cardTexture: String? = null,
        val cardTextureGui: String? = null,
        val cardTextureOutline: String? = null,
    )

    class TeamColors(
        val tileAchievedColor: Color = Color.fromInt(0xff55ff55.toInt()),
        val tileFlashingColor: Color = Color.fromInt(0xffb24cd8.toInt()),
        val tileProgressColor: Color = Color.fromInt(0xff309f30.toInt()),
        val textX: Int = 12,
        val textY: Int = 10,
        val textColor: Color = Color.BLACK,
        val outlineColor: Color = Color.TRANSPARENT,
        val cardTexture: ResourceLocation = ResourceLocation.fromNamespaceAndPath("minecraft", "bingo/card_preview"),
        val cardTextureGui: ResourceLocation = ResourceLocation.fromNamespaceAndPath("minecraft", "bingo/card_preview"),
        val cardTextureOutline: ResourceLocation = ResourceLocation.fromNamespaceAndPath("minecraft", "bingo/outline"),
    )

    private fun <V> getValue(team: Team?, property: KProperty1<Team, V>): V =
        team?.let { property.get(it) }
            ?: property.get(default)

    private fun String.parseIdentifier(): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(substringBefore(':'), substringAfter(':'))!!

    fun getTeamColors(
        teamKey: BingoTeamKey?,
        formatting: ChatFormatting?,
    ): TeamColors {
        val colors = teamKey?.id?.let { teams[it] }
            ?: teamKey?.label?.let { teams[it] }
            ?: formatting?.name?.lowercase()?.let { teams[it] }
            ?: default

        return TeamColors(
            tileAchievedColor = getValue(colors, Team::tileAchievedColor)
                ?.let { Color.fromInt(it.rgb) }
                ?: DEFAULTS.tileAchievedColor,
            tileFlashingColor = getValue(colors, Team::tileFlashingColor)
                ?.let { Color.fromInt(it.rgb) }
                ?: DEFAULTS.tileFlashingColor,
            tileProgressColor = getValue(colors, Team::tileProgressColor)
                ?.let { Color.fromInt(it.rgb) }
                ?: DEFAULTS.tileProgressColor,
            textX = getValue(colors, Team::textX) ?: DEFAULTS.textX,
            textY = getValue(colors, Team::textY) ?: DEFAULTS.textY,
            textColor = getValue(colors, Team::textColor)
                ?.let { Color.fromInt(it.rgb) }
                ?: DEFAULTS.textColor,
            outlineColor = getValue(colors, Team::outlineColor)
                ?.let { Color.fromInt(it.rgb) }
                ?: formatting?.color?.let { Color.fromInt(it) }?.copy(a = 255)
                ?: DEFAULTS.outlineColor,
            cardTexture = getValue(colors, Team::cardTexture)
                ?.parseIdentifier()
                ?: DEFAULTS.cardTexture,
            cardTextureGui = getValue(colors, Team::cardTextureGui)
                ?.parseIdentifier()
                ?: getValue(colors, Team::cardTexture)?.parseIdentifier()
                ?: DEFAULTS.cardTextureGui,
            cardTextureOutline = getValue(colors, Team::cardTextureOutline)
                ?.parseIdentifier()
                ?: DEFAULTS.cardTextureOutline,
        )
    }

}
