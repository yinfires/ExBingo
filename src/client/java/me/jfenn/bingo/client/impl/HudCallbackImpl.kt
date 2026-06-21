package me.jfenn.bingo.client.impl

import me.jfenn.bingo.client.impl.draw.DrawService
import me.jfenn.bingo.client.platform.event.model.HudRenderEvent
import me.jfenn.bingo.platform.event.IEventBus
import net.neoforged.neoforge.client.event.RenderGuiEvent
import net.neoforged.neoforge.common.NeoForge

class HudCallbackImpl(
    private val eventBus: IEventBus,
) {
    init {
        NeoForge.EVENT_BUS.addListener(RenderGuiEvent.Post::class.java) { event ->
            val delta = event.partialTick.getGameTimeDeltaPartialTick(false)
            eventBus.emit(
                HudRenderEvent,
                HudRenderEvent(
                    drawService = DrawService(event.guiGraphics)
                        .also { it.delta = delta },
                    delta = delta
                )
            )
        }
    }
}
