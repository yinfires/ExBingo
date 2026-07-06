package me.jfenn.bingo.impl

import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.commands.ISignedMessage
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.model.*
import me.jfenn.bingo.platform.text.IText
import net.minecraft.network.chat.Component
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.util.profiling.ProfilerFiller
import net.neoforged.bus.api.ICancellableEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.AddReloadListenerEvent
import net.neoforged.neoforge.event.ServerChatEvent
import net.neoforged.neoforge.event.TagsUpdatedEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent as NeoForgeAttackEntityEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent as NeoForgePlayerEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.UUID

class ServerEventsImpl(
    eventBus: IEventBus,
) {
    private fun getPlayerImpl(player: ServerPlayer): IPlayerHandle {
        return PlayerHandle(player, null)
    }

    private fun ActionResult<Unit>.toActionResult(): InteractionResult {
        return when (this) {
            is ActionResult.Fail -> InteractionResult.FAIL
            is ActionResult.Success -> InteractionResult.SUCCESS
            is ActionResult.Pass -> InteractionResult.PASS
        }
    }

    private fun <T> ActionResult<T>.toTypedActionResult(): InteractionResultHolder<T> {
        return when (this) {
            is ActionResult.Fail -> InteractionResultHolder.fail(value)
            is ActionResult.Success -> InteractionResultHolder.success(value)
            is ActionResult.Pass -> InteractionResultHolder.pass(value)
        }
    }

    init {
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent::class.java) { event ->
            eventBus.emit(ServerEvent.Started, ServerEvent(event.server))
        }

        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent::class.java) { event ->
            eventBus.emit(ServerEvent.Stopped, ServerEvent(event.server))
        }

        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Pre::class.java) { event ->
            eventBus.emit(TickEvent.Start, TickEvent(event.server.tickCount))
        }

        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post::class.java) { event ->
            eventBus.emit(TickEvent.End, TickEvent(event.server.tickCount))
        }

        NeoForge.EVENT_BUS.addListener(NeoForgePlayerEvent.PlayerLoggedInEvent::class.java) { event ->
            val player = event.entity as? ServerPlayer ?: return@addListener
            val playerImpl = getPlayerImpl(player)
            eventBus.emit(PlayerEvent.Init, PlayerEvent(playerImpl))
            eventBus.emit(PlayerEvent.Join, PlayerEvent(playerImpl))
            eventBus.emit(PlayerEvent.ChannelRegister, PlayerEvent(playerImpl))
        }

        NeoForge.EVENT_BUS.addListener(NeoForgePlayerEvent.PlayerLoggedOutEvent::class.java) { event ->
            val player = event.entity as? ServerPlayer ?: return@addListener
            eventBus.emit(PlayerEvent.Disconnect, PlayerEvent(getPlayerImpl(player)))
        }

        NeoForge.EVENT_BUS.addListener(NeoForgePlayerEvent.PlayerRespawnEvent::class.java) { event ->
            val player = event.entity as? ServerPlayer ?: return@addListener
            eventBus.emit(PlayerEvent.AfterRespawn, PlayerEvent(getPlayerImpl(player)))
        }

        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent::class.java) { event ->
            val world = event.level as? net.minecraft.server.level.ServerLevel ?: return@addListener
            eventBus.emit(EntityLoadEvent, EntityLoadEvent(world.server, event.entity, world))
        }

        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickBlock::class.java) { event ->
            val player = event.entity as? ServerPlayer ?: return@addListener
            val playerImpl = getPlayerImpl(player)
            val results = eventBus.emit(UseBlockEvent, UseBlockEvent(player.server, playerImpl, event.level, event.hand, event.hitVec))
            ActionResult.collapse(results)
                ?.applyTo(event)
        }

        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.EntityInteract::class.java) { event ->
            val player = event.entity as? ServerPlayer ?: return@addListener
            val playerImpl = getPlayerImpl(player)
            val results = eventBus.emit(UseEntityEvent, UseEntityEvent(player.server, playerImpl, event.level, event.hand, event.target, null))
            ActionResult.collapse(results)
                ?.applyTo(event)
        }

        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.EntityInteractSpecific::class.java) { event ->
            val player = event.entity as? ServerPlayer ?: return@addListener
            val playerImpl = getPlayerImpl(player)
            val results = eventBus.emit(UseEntityEvent, UseEntityEvent(player.server, playerImpl, event.level, event.hand, event.target, null))
            ActionResult.collapse(results)
                ?.applyTo(event)
        }

        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickItem::class.java) { event ->
            val player = event.entity as? ServerPlayer ?: return@addListener
            val playerImpl = getPlayerImpl(player)
            val results = eventBus.emit(UseItemEvent, UseItemEvent(player.server, playerImpl, event.level, event.hand))
            ActionResult.collapse(results)
                ?.map { it?.stack }
                ?.applyHolderTo(event)
        }

        NeoForge.EVENT_BUS.addListener(NeoForgeAttackEntityEvent::class.java) { event ->
            val player = event.entity as? ServerPlayer ?: return@addListener
            val playerImpl = getPlayerImpl(player)
            val results = eventBus.emit(AttackEntityEvent, AttackEntityEvent(player.server, playerImpl, player.level(), InteractionHand.MAIN_HAND, event.target, null))
            ActionResult.collapse(results)
                ?.applyTo(event)
        }

        NeoForge.EVENT_BUS.addListener(AddReloadListenerEvent::class.java) { event ->
            event.addListener(
            object : PreparableReloadListener {
                override fun reload(
                    synchronizer: PreparableReloadListener.PreparationBarrier,
                    manager: ResourceManager,
                    prepareProfiler: ProfilerFiller,
                    applyProfiler: ProfilerFiller,
                    prepareExecutor: Executor,
                    applyExecutor: Executor
                ): CompletableFuture<Void> {
                    val reloadEvent = ReloadEvent(
                        resourceManager = manager,
                        prepareExecutor = prepareExecutor,
                        applyExecutor = applyExecutor,
                        whenPrepared = { obj -> synchronizer.wait(obj) },
                    )
                    return eventBus.emit(ReloadEvent, reloadEvent)
                        .takeIf { it.isNotEmpty() }
                        ?.let { CompletableFuture.allOf(*it.toTypedArray()) }
                        ?: CompletableFuture.completedFuture(reloadEvent)
                            .thenCompose { synchronizer.wait(it) }
                            .thenAcceptAsync({}, applyExecutor)
                }

                override fun getName(): String = MOD_ID_BINGO
            }
            )
        }

        NeoForge.EVENT_BUS.addListener(TagsUpdatedEvent::class.java) {
            eventBus.emit(ReloadEvent.After, ReloadEvent.After())
        }

        NeoForge.EVENT_BUS.addListener(ServerChatEvent::class.java) { chatEvent ->
            val event = AllowChatMessageEvent(
                message = PlainSignedMessage(chatEvent.player.uuid, chatEvent.message, chatEvent.rawText),
                player = getPlayerImpl(chatEvent.player),
            )
            val isAllowed = eventBus.emit(AllowChatMessageEvent, event)
                .all { it }

            if (!isAllowed) chatEvent.setCanceled(true)
        }
    }

    private fun ActionResult<Unit>.applyTo(event: ICancellableEvent) {
        val result = toActionResult()
        if (result != InteractionResult.PASS) event.setCanceled(true)
        when (event) {
            is PlayerInteractEvent.RightClickBlock -> event.cancellationResult = result
            is PlayerInteractEvent.EntityInteract -> event.cancellationResult = result
            is PlayerInteractEvent.EntityInteractSpecific -> event.cancellationResult = result
            is PlayerInteractEvent.RightClickItem -> event.cancellationResult = result
        }
    }

    private fun <T> ActionResult<T>.applyHolderTo(event: PlayerInteractEvent.RightClickItem) {
        val result = toTypedActionResult().result
        if (result != InteractionResult.PASS) {
            event.cancellationResult = result
            event.setCanceled(true)
        }
    }

    private class PlainSignedMessage(
        override val sender: UUID,
        private val component: Component,
        override val raw: String,
    ) : ISignedMessage {
        override val text: IText get() = TextImpl(component.copy())
        override fun withUnsignedContent(text: IText): ISignedMessage {
            return PlainSignedMessage(sender, text.value, text.value.string)
        }
    }
}
