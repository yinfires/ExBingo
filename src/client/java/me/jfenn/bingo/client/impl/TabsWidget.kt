package me.jfenn.bingo.client.impl

import me.jfenn.bingo.client.platform.ITabsWidget
import me.jfenn.bingo.client.platform.ITabsWidgetFactory
import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.common.utils.EventListener
import me.jfenn.bingo.platform.utils.IEventListener
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.components.tabs.Tab
import net.minecraft.client.gui.components.tabs.TabManager
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.tabs.TabNavigationBar
import net.minecraft.network.chat.Component
import java.util.function.Consumer

class TabsWidgetImpl(
    private val tabImpls: List<TabImpl>,
    private val manager: TabManager,
    override var widget: TabNavigationBar,
    val onTabUpdate: EventListener<Unit>,
) : ITabsWidget {
    override val height: Int = 24
    override var width: Int = 0
        set(value) {
            field = value
            widget.setWidth(value)
            widget.arrangeElements()
        }

    override var currentTab: Int
        get() = tabImpls.indexOf(manager.currentTab)
        set(value) {
            widget.selectTab(value, false)
        }

    override val onTabChanged: IEventListener<Int> = EventListener()

    private var prevTab: Int = currentTab
    init {
        onTabUpdate {
            val tab = currentTab
            if (prevTab != tab) {
                prevTab = tab
                onTabChanged(tab)
            }
        }
    }
}

class TabImpl(private val text: Component) : Tab {
    override fun getTabTitle(): Component {
        return text
    }

    override fun visitChildren(consumer: Consumer<AbstractWidget>) {
        // Send at least one event to run onTabChanged
    }

    override fun doLayout(tabArea: ScreenRectangle) {}
}

object TabsWidgetFactory : ITabsWidgetFactory {
    override fun create(tabs: List<IText>): ITabsWidget {
        val onTabUpdate = EventListener<Unit>()
        val manager = TabManager({ onTabUpdate.invoke(Unit) }, {})
        val tabImpls = tabs.map { TabImpl(it.value) }

        return TabsWidgetImpl(
            tabImpls = tabImpls,
            manager = manager,
            widget = TabNavigationBar.builder(manager, 0)
                .addTabs(*tabImpls.toTypedArray())
                .build(),
            onTabUpdate = onTabUpdate,
        )
    }
}
