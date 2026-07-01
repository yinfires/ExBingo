package me.jfenn.bingo.client.integrations

import me.jfenn.bingo.client.common.hud.card.ClientCardBufferRenderer
import me.jfenn.bingo.client.common.state.BingoHudState
import me.jfenn.bingo.client.platform.IClient
import me.jfenn.bingo.client.platform.IOptionsAccessor
import me.jfenn.bingo.client.platform.event.model.ClientTickEvent
import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.platform.IModEnvironment
import me.jfenn.bingo.platform.event.IEventBus
import net.minecraft.client.gui.screens.ChatScreen
import org.slf4j.Logger
import kotlin.math.ceil

internal class CompassHudCompat(
    private val log: Logger,
    private val environment: IModEnvironment,
    private val eventBus: IEventBus,
    private val config: BingoConfig,
    private val state: BingoHudState,
    private val optionsAccessor: IOptionsAccessor,
    private val client: IClient,
) {
    private val adapters = mutableListOf<CompassOverlayAdapter>()
    private val pendingTargets = COMPASS_TARGETS.toMutableList()
    private var loggedMissingTargets = false

    init {
        if (environment.envType == IModEnvironment.EnvType.CLIENT) {
            pendingTargets.removeIf { !environment.isModLoaded(it.modId) }
            if (pendingTargets.isNotEmpty()) {
                eventBus.register(ClientTickEvent.End) {
                    if (client.isPaused) return@register

                    initializePendingAdapters()
                    adapters.forEach(::updateOverlayOffset)
                }
            }
        }
    }

    private fun initializePendingAdapters() {
        val iterator = pendingTargets.iterator()
        while (iterator.hasNext()) {
            val target = iterator.next()
            when (val adapter = tryCreateAdapter(target)) {
                null -> Unit
                MISSING -> {
                    log.warn("Unable to apply {} HUD compatibility; required client config fields were not found.", target.displayName)
                    iterator.remove()
                }

                else -> {
                    adapters += adapter
                    iterator.remove()
                }
            }
        }

        if (pendingTargets.isNotEmpty() && !loggedMissingTargets) {
            log.debug(
                "Waiting for compass HUD compatibility targets to finish loading: {}",
                pendingTargets.joinToString { it.displayName },
            )
            loggedMissingTargets = true
        } else if (pendingTargets.isEmpty()) {
            loggedMissingTargets = false
        }
    }

    private fun tryCreateAdapter(target: CompassOverlayTarget): CompassOverlayAdapter? {
        return try {
            val configHandlerClass = Class.forName(target.configHandlerClass)
            val clientConfig = configHandlerClass.getField("CLIENT").get(null) ?: return null
            val overlayLineOffsetEntry = getConfigEntry(clientConfig, "overlayLineOffset") ?: return MISSING
            val overlaySideEntry = getConfigEntry(clientConfig, "overlaySide") ?: return MISSING

            CompassOverlayAdapter(
                target = target,
                overlayLineOffsetEntry = overlayLineOffsetEntry,
                overlaySideEntry = overlaySideEntry,
            )
        } catch (_: ClassNotFoundException) {
            null
        } catch (e: ReflectiveOperationException) {
            log.warn("Unable to initialize {} HUD compatibility.", target.displayName, e)
            MISSING
        }
    }

    private fun getConfigEntry(clientConfig: Any, fieldName: String): Any? {
        return try {
            clientConfig.javaClass.getField(fieldName).get(clientConfig)
        } catch (_: NoSuchFieldException) {
            null
        } catch (_: IllegalAccessException) {
            null
        }
    }

    private fun updateOverlayOffset(adapter: CompassOverlayAdapter) {
        val currentOffset = adapter.getLineOffset() ?: return
        val requiredOffset = getRequiredLineOffset(adapter.getOverlaySide())

        if (requiredOffset == null) {
            val baseLineOffset = adapter.baseLineOffset ?: currentOffset
            if (adapter.compatibilityApplied && currentOffset != baseLineOffset) {
                adapter.setLineOffset(baseLineOffset)
            }
            adapter.baseLineOffset = baseLineOffset
            adapter.compatibilityApplied = false
            return
        }

        if (!adapter.compatibilityApplied) {
            adapter.baseLineOffset = currentOffset
        }

        val targetOffset = maxOf(adapter.baseLineOffset ?: currentOffset, requiredOffset)
        if (currentOffset != targetOffset) {
            adapter.setLineOffset(targetOffset)
        }
        adapter.compatibilityApplied = true
    }

    private fun getRequiredLineOffset(overlaySide: OverlaySide?): Int? {
        if (overlaySide == null) return null
        if (!config.client.enableHud || state.cards.isEmpty() || optionsAccessor.isHudHidden()) return null
        if (config.client.hideOnF3 && optionsAccessor.isDebugEnabled()) return null

        val screen = client.screen
        if (screen != null && screen !is ChatScreen) return null
        if (config.client.hideOnChat && screen is ChatScreen) return null
        if (screen?.javaClass?.packageName?.startsWith("me.jfenn.bingo") == true) return null

        val cardAlignment = config.client.cardAlignment
        if (cardAlignment.y != 0) return null

        val isSameHorizontalSide = when (overlaySide) {
            OverlaySide.LEFT -> cardAlignment.x == 0
            OverlaySide.RIGHT -> cardAlignment.x > 0
        }
        if (!isSameHorizontalSide) return null

        val cardBottom = config.client.cardOffsetY + (ClientCardBufferRenderer.CARD_HEIGHT * config.client.cardScale)
        return ceil(((cardBottom + HUD_BOTTOM_PADDING) - COMPASS_TEXT_BASE_Y) / COMPASS_LINE_HEIGHT).toInt()
            .coerceIn(MIN_OVERLAY_LINE_OFFSET, MAX_OVERLAY_LINE_OFFSET)
    }

    private data class CompassOverlayTarget(
        val modId: String,
        val displayName: String,
        val configHandlerClass: String,
    )

    private data class CompassOverlayAdapter(
        val target: CompassOverlayTarget,
        val overlayLineOffsetEntry: Any,
        val overlaySideEntry: Any,
        var baseLineOffset: Int? = null,
        var compatibilityApplied: Boolean = false,
    ) {
        fun getLineOffset(): Int? {
            return try {
                (overlayLineOffsetEntry.javaClass.getMethod("get").invoke(overlayLineOffsetEntry) as? Number)?.toInt()
            } catch (_: ReflectiveOperationException) {
                null
            }
        }

        fun setLineOffset(value: Int): Boolean {
            return try {
                val setMethod = overlayLineOffsetEntry.javaClass.methods.firstOrNull {
                    it.name == "set" && it.parameterCount == 1
                } ?: throw NoSuchMethodException("${overlayLineOffsetEntry.javaClass.name}.set")

                setMethod.invoke(overlayLineOffsetEntry, value)
                true
            } catch (_: ReflectiveOperationException) {
                false
            }
        }

        fun getOverlaySide(): OverlaySide? {
            return try {
                when (overlaySideEntry.javaClass.getMethod("get").invoke(overlaySideEntry)?.toString()) {
                    "LEFT" -> OverlaySide.LEFT
                    "RIGHT" -> OverlaySide.RIGHT
                    else -> null
                }
            } catch (_: ReflectiveOperationException) {
                null
            }
        }
    }

    private enum class OverlaySide {
        LEFT,
        RIGHT,
    }

    private companion object {
        val COMPASS_TARGETS = listOf(
            CompassOverlayTarget(
                modId = "naturescompass",
                displayName = "Nature's Compass",
                configHandlerClass = "com.chaosthedude.naturescompass.config.ConfigHandler",
            ),
            CompassOverlayTarget(
                modId = "explorerscompass",
                displayName = "Explorer's Compass",
                configHandlerClass = "com.chaosthedude.explorerscompass.config.ConfigHandler",
            ),
        )

        const val COMPASS_TEXT_BASE_Y = 7f
        const val COMPASS_LINE_HEIGHT = 9f
        const val HUD_BOTTOM_PADDING = 4f
        const val MIN_OVERLAY_LINE_OFFSET = 0
        const val MAX_OVERLAY_LINE_OFFSET = 50

        val MISSING = CompassOverlayAdapter(
            target = CompassOverlayTarget("", "", ""),
            overlayLineOffsetEntry = Any(),
            overlaySideEntry = Any(),
        )
    }
}
