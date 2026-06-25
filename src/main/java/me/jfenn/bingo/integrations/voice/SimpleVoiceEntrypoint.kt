package me.jfenn.bingo.integrations.voice

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin
import de.maxhenkel.voicechat.api.VoicechatPlugin
import de.maxhenkel.voicechat.api.VoicechatServerApi
import de.maxhenkel.voicechat.api.events.EventRegistration
import de.maxhenkel.voicechat.api.events.PlayerConnectedEvent
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Simple Voice Chat plugin entrypoint.
 *
 * On NeoForge/Forge, Simple Voice Chat discovers plugins by scanning for classes
 * annotated with [ForgeVoicechatPlugin] and instantiating them via their no-arg
 * constructor (unlike Fabric, which uses the `voicechat` entrypoint in fabric.mod.json).
 * It must therefore be a regular class with a public no-arg constructor; shared state
 * lives in the [companion object] so [SimpleVoiceApi] can read it.
 */
@ForgeVoicechatPlugin
internal class SimpleVoiceEntrypoint : VoicechatPlugin {

    companion object {
        private val logger = LoggerFactory.getLogger("ExBingo")
        var api: VoicechatServerApi? = null
        val onPlayerConnectedGroups = ConcurrentHashMap<UUID, UUID>()
    }

    override fun getPluginId(): String {
        return "exbingo"
    }

    override fun registerEvents(registration: EventRegistration) {
        registration.registerEvent(VoicechatServerStartedEvent::class.java, this::onServerStarted)
        registration.registerEvent(VoicechatServerStoppedEvent::class.java, this::onServerStopped)
        registration.registerEvent(PlayerConnectedEvent::class.java, this::onPlayerConnected)
    }

    fun onServerStarted(event: VoicechatServerStartedEvent) {
        logger.debug("[SimpleVoiceEntrypoint] Voice server started")
        api = event.voicechat
    }

    fun onServerStopped(event: VoicechatServerStoppedEvent) {
        logger.debug("[SimpleVoiceEntrypoint] Voice server stopped")
        api = null
    }

    fun onPlayerConnected(event: PlayerConnectedEvent) {
        try {
            // If a player is assigned a group before they log in, we need to defer
            // the group join call until after they are connected
            // - this prevents clientside HUD de-sync
            val playerId = event.connection.player.uuid
            val groupId = onPlayerConnectedGroups.remove(event.connection.player.uuid)
            if (groupId != null) {
                SimpleVoiceApi().GroupHandle(event.voicechat, groupId)
                    .addPlayer(playerId)
            }
        } catch (e: Throwable) {
            logger.error("[SimpleVoiceEntrypoint] Error running onPlayerConnected:", e)
        }
    }
}