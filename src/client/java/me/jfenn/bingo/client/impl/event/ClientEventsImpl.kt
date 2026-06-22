package me.jfenn.bingo.client.impl.event

import me.jfenn.bingo.client.impl.screen.ScreenHelperImpl
import me.jfenn.bingo.client.platform.event.model.*
import me.jfenn.bingo.platform.event.IEventBus
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.minecraft.util.profiling.ProfilerFiller
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent as NeoForgeClientTickEvent
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent
import net.neoforged.neoforge.client.event.ScreenEvent as NeoForgeScreenEvent
import net.neoforged.neoforge.common.NeoForge
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

class ClientEventsImpl(
    private val eventBus: IEventBus,
) {
    init {
        sharedEventBus = eventBus

        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn::class.java) {
            eventBus.emit(ClientServerEvent.Join, ClientServerEvent())
            eventBus.emit(ClientServerEvent.ChannelRegister, ClientServerEvent())
        }

        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut::class.java) {
            eventBus.emit(ClientServerEvent.Disconnect, ClientServerEvent())
        }

        NeoForge.EVENT_BUS.addListener(NeoForgeClientTickEvent.Post::class.java) {
            eventBus.emit(ClientTickEvent.End, ClientTickEvent())
        }

        NeoForge.EVENT_BUS.addListener(NeoForgeScreenEvent.Init.Post::class.java) { event ->
            eventBus.emit(
                ScreenEvent.AfterInit,
                ScreenEvent(
                    type = when (event.screen) {
                        is TitleScreen -> ScreenType.TitleScreen
                        else -> ScreenType.Other
                    },
                    screen = ScreenHelperImpl(event.screen, event::addListener),
                )
            )
        }
    }

    companion object {
        private var sharedEventBus: IEventBus? = null

        @JvmStatic
        fun registerReloadListeners(event: RegisterClientReloadListenersEvent) {
            val eventBus = sharedEventBus ?: return
            event.registerReloadListener(
                object : PreparableReloadListener {
                    private val identifier = ResourceLocation.fromNamespaceAndPath("exbingo", "bingo_client")

                    override fun reload(
                        synchronizer: PreparableReloadListener.PreparationBarrier,
                        manager: ResourceManager,
                        prepareProfiler: ProfilerFiller,
                        applyProfiler: ProfilerFiller,
                        prepareExecutor: Executor,
                        applyExecutor: Executor,
                    ): CompletableFuture<Void> {
                        return CompletableFuture
                            .supplyAsync({ manager }, prepareExecutor)
                            .thenCompose { synchronizer.wait(it) }
                            .thenAcceptAsync({ eventBus.emit(ClientReloadEvent, ClientReloadEvent(manager)) }, applyExecutor)
                    }

                    override fun getName(): String = identifier.toString()
                }
            )
        }
    }
}
