package me.jfenn.bingo.client.platform

import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.utils.IEventListener
import net.minecraft.client.gui.components.tabs.TabNavigationBar

interface ITabsWidget {
    var width: Int
    val height: Int
    val widget: TabNavigationBar

    var currentTab: Int
    val onTabChanged: IEventListener<Int>
}

interface ITabsWidgetFactory {
    fun create(tabs: List<IText>): ITabsWidget
}
