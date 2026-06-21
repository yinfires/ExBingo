package me.jfenn.bingo.impl.event

import me.jfenn.bingo.platform.event.model.ApplicationCloseEvent
import me.jfenn.bingo.platform.event.IEventBus
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.server.ServerStoppingEvent

class ServerCloseEvent(
    private val eventBus: IEventBus,
) {
    init {
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent::class.java) {
            eventBus.emit(ApplicationCloseEvent, Unit)
        }
    }
}
