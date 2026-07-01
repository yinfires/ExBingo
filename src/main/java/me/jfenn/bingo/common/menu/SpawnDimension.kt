package me.jfenn.bingo.common.menu

import me.jfenn.bingo.common.options.listSelectableSpawnDimensions
import me.jfenn.bingo.common.options.OptionsService
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.utils.formatTitle
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.IServerWorldFactory
import org.joml.Vector3d

internal const val MENU_DIMENSION_WIDTH = 2.0

internal fun MenuComponent.registerDimensionMenu(
    position: Vector3d,
    width: Double = MENU_DIMENSION_WIDTH,
    height: Double,
    state: BingoState = koinScope.get(),
    serverWorldFactory: IServerWorldFactory = koinScope.get(),
    optionsService: OptionsService = koinScope.get(),
) {
    val dimensions = serverWorldFactory.listSelectableSpawnDimensions()

    var dimension by state.options::spawnDimension
    val indexProp = DelegatedProperty(
        getter = { dimensions.indexOfFirst { it == dimension } },
        setter = { dimension = dimensions[it] },
    )

    val dimensionLabels = dimensions.map { identifier ->
        // 使用标准维度翻译键 dimension.<namespace>.<path>，复用各模组/原版及本资源包提供的维度名翻译，
        // 不再维护独立的 bingo.dimension.* 键。
        val key = "dimension." + identifier.replace(':', '.').replace('/', '.')
        val fallback = identifier.substringAfterLast(':').formatTitle()
        text.translatable(key, fallback)
    }

    registerRadioMenu(
        position = position,
        width = width,
        height = height,
        title = text.string(StringKey.OptionsSpawnDimension),
        options = dimensionLabels,
        selectedIndexProp = indexProp,
    ) { player, index ->
        optionsService.setSpawnDimension(
            OptionsService.Context(player),
            dimensions[index]
        )
    }
}
