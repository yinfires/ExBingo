package me.jfenn.bingo.common.menu

import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.common.utils.plus
import me.jfenn.bingo.platform.IPlayerHandle
import net.minecraft.ChatFormatting
import org.joml.Vector3d
import kotlin.math.ceil

private class RadioMenuState(
    var selectedPage: Int
)

internal fun MenuComponent.registerRadioMenu(
    position: Vector3d,
    width: Double,
    height: Double,
    title: IText,
    options: List<IText>,
    tooltips: List<List<IText>> = emptyList(),
    optionsPerPage: Int = (height / (MENU_LINE_HEIGHT + MENU_LINE_PADDING)).toInt() - 2,
    selectedIndexProp: MutableProperty<Int>,
    onChange: (IPlayerHandle, Int) -> Unit = { _, _ -> },
) = registerRadioMenu(
    position = position,
    width = width,
    height = height,
    title = title,
    optionsProvider = { options },
    tooltipsProvider = { tooltips },
    optionsPerPage = optionsPerPage,
    selectedIndexProp = selectedIndexProp,
    onChange = onChange,
)

/**
 * Like [registerRadioMenu], but the option/tooltip lists are read fresh on every render via the
 * providers, so the menu reflects live changes (e.g. boards disabled by an op) without needing
 * the whole lobby menu to be rebuilt.
 */
internal fun MenuComponent.registerRadioMenu(
    position: Vector3d,
    width: Double,
    height: Double,
    title: IText,
    optionsProvider: () -> List<IText>,
    tooltipsProvider: () -> List<List<IText>> = { emptyList() },
    optionsPerPage: Int = (height / (MENU_LINE_HEIGHT + MENU_LINE_PADDING)).toInt() - 2,
    selectedIndexProp: MutableProperty<Int>,
    onChange: (IPlayerHandle, Int) -> Unit = { _, _ -> },
) {
    val offset = Vector3d()

    registerTitlePanel(
        position = position + offset.sub(0.0, 2*MENU_LINE_PADDING + MENU_LINE_HEIGHT, 0.0),
        width = width,
        title = title,
    )

    var selectedIndex by selectedIndexProp
    val pageState = RadioMenuState(
        selectedPage = selectedIndex / optionsPerPage
    )

    val optionHeight = ((height - 3*MENU_LINE_PADDING - 2*MENU_LINE_HEIGHT) / optionsPerPage) - MENU_LINE_PADDING
    for (i in 0 until optionsPerPage) {
        val itemIndex by computedProperty {
            (pageState.selectedPage*optionsPerPage + i).takeIf { it in optionsProvider().indices }
        }

        registerTileButton(
            position = position + offset.sub(0.0, MENU_LINE_PADDING + optionHeight, 0.0),
            width = width,
            height = optionHeight,
            text = text.empty(),
            textProp = computedProperty {
                itemIndex?.let { optionsProvider().getOrNull(it) } ?: text.empty()
            },
            tooltipProp = computedProperty {
                itemIndex?.let { tooltipsProvider().getOrNull(it) }
            },
            isActiveProp = computedProperty { selectedIndex == itemIndex },
        ) { player ->
            itemIndex?.also { selectedIndex = it }?.also { onChange(player, it) }
        }
    }

    registerNumberInput(
        position = position + offset.sub(0.0, MENU_LINE_PADDING + MENU_LINE_HEIGHT, 0.0),
        width = width,
        height = MENU_LINE_HEIGHT,
        valueProp = propertyRef(pageState::selectedPage),
        minValueProp = ConstantProperty(0),
        maxValueProp = computedProperty {
            ceil(optionsProvider().size / optionsPerPage.toFloat()).toInt() - 1
        },
        isVisible = { optionsProvider().size > optionsPerPage },
        increaseLabel = "»",
        decreaseLabel = "«",
        format = { page ->
            val pagesLength = ceil(optionsProvider().size / optionsPerPage.toFloat()).toInt()
            (0 until pagesLength)
                .map { i ->
                    val hasSelected = selectedIndex in i*optionsPerPage until (i+1)*optionsPerPage
                    when {
                        page == i -> text.literal(if (hasSelected) "■" else "□").formatted(ChatFormatting.WHITE)
                        else -> text.literal(if (hasSelected) "◆" else "◇").formatted(ChatFormatting.GRAY)
                    }
                }
                .let { text.joinText(it, text.literal(" ")) }
        }
    )
}
