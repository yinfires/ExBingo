package me.jfenn.bingo.client.integrations

import me.jfenn.bingo.client.platform.event.model.ClientTickEvent
import me.jfenn.bingo.platform.IModEnvironment
import me.jfenn.bingo.platform.event.ICallbackHandle
import me.jfenn.bingo.platform.event.IEventBus
import org.slf4j.Logger

internal class SimpleVoiceChatHudCompat(
    private val log: Logger,
    private val environment: IModEnvironment,
    private val eventBus: IEventBus,
) {
    private var retryHandle: ICallbackHandle? = null
    private var finished = false

    init {
        if (
            environment.envType != IModEnvironment.EnvType.CLIENT ||
            !environment.isModLoaded("voicechat")
        ) {
            finished = true
        } else if (!tryApply()) {
            retryHandle = eventBus.register(ClientTickEvent.End) {
                if (tryApply()) {
                    retryHandle?.close()
                    retryHandle = null
                }
            }
        }
    }

    private fun tryApply(): Boolean {
        if (finished) return true

        val clientConfig = try {
            val voicechatClient = Class.forName("de.maxhenkel.voicechat.VoicechatClient")
            voicechatClient.getField("CLIENT_CONFIG").get(null)
        } catch (e: ClassNotFoundException) {
            log.debug("Simple Voice Chat client classes were not found; skipping HUD compatibility.")
            finished = true
            return true
        } catch (e: ReflectiveOperationException) {
            log.warn("Unable to inspect Simple Voice Chat client config; skipping HUD compatibility.", e)
            finished = true
            return true
        } ?: return false

        val xEntry = getConfigEntry(clientConfig, "groupPlayerIconPosX") ?: return true
        val yEntry = getConfigEntry(clientConfig, "groupPlayerIconPosY") ?: return true

        val currentX = getEntryValue(xEntry) ?: return true
        val currentY = getEntryValue(yEntry) ?: return true

        if (currentX == DEFAULT_GROUP_PLAYER_ICON_X && currentY == DEFAULT_GROUP_PLAYER_ICON_Y) {
            if (!setEntryValue(xEntry, BINGO_GROUP_PLAYER_ICON_X) ||
                !setEntryValue(yEntry, BINGO_GROUP_PLAYER_ICON_Y)
            ) {
                finished = true
                return true
            }

            log.info(
                "Moved Simple Voice Chat group player HUD icons from {},{} to {},{} to avoid the Bingo HUD.",
                currentX,
                currentY,
                BINGO_GROUP_PLAYER_ICON_X,
                BINGO_GROUP_PLAYER_ICON_Y,
            )
        } else {
            log.debug(
                "Keeping custom Simple Voice Chat group player HUD icon position {},{}.",
                currentX,
                currentY,
            )
        }

        finished = true
        return true
    }

    private fun getConfigEntry(clientConfig: Any, fieldName: String): Any? {
        return try {
            clientConfig.javaClass.getField(fieldName).get(clientConfig)
        } catch (e: ReflectiveOperationException) {
            log.warn("Simple Voice Chat client config field {} was not found; skipping HUD compatibility.", fieldName, e)
            finished = true
            null
        }
    }

    private fun getEntryValue(entry: Any): Int? {
        return try {
            (entry.javaClass.getMethod("get").invoke(entry) as? Number)?.toInt()
        } catch (e: ReflectiveOperationException) {
            log.warn("Unable to read Simple Voice Chat HUD config entry; skipping HUD compatibility.", e)
            finished = true
            null
        }
    }

    private fun setEntryValue(entry: Any, value: Int): Boolean {
        return try {
            val setMethod = entry.javaClass.methods.firstOrNull {
                it.name == "set" && it.parameterCount == 1
            } ?: throw NoSuchMethodException("${entry.javaClass.name}.set")

            setMethod.invoke(entry, value)
            true
        } catch (e: ReflectiveOperationException) {
            log.warn("Unable to update Simple Voice Chat HUD config entry; skipping HUD compatibility.", e)
            false
        }
    }

    private companion object {
        const val DEFAULT_GROUP_PLAYER_ICON_X = 4
        const val DEFAULT_GROUP_PLAYER_ICON_Y = 4
        const val BINGO_GROUP_PLAYER_ICON_X = 124
        const val BINGO_GROUP_PLAYER_ICON_Y = 4
    }
}
