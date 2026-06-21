package me.jfenn.bingo.client.impl.event

import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.model.ApplicationCloseEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.GameShuttingDownEvent

internal class ClientCloseEvent(
    private val eventBus: IEventBus,
)  {
    init {
        NeoForge.EVENT_BUS.addListener(GameShuttingDownEvent::class.java) {
            eventBus.emit(ApplicationCloseEvent, Unit)
        }
    }
}
