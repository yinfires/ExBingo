package me.jfenn.bingo.client.integrations

import me.jfenn.bingo.client.common.hud.screen.BingoCardPlacementScreen
import me.jfenn.bingo.client.common.settings.ClientSettingsService
import me.jfenn.bingo.client.common.sound.SoundService
import me.jfenn.bingo.client.platform.IClient
import me.jfenn.bingo.client.platform.renderer.IDrawServiceFactory
import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.config.ConfigService
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.platform.IModEnvironment
import me.jfenn.bingo.platform.event.IEventBus
import net.minecraft.client.gui.screens.Screen

internal interface YetAnotherConfigLibIntegration {

    fun isInstalled(): Boolean

    fun buildConfigScreen(parent: Screen): Screen?

    companion object {
        fun hasYACL(environment: IModEnvironment) = false

        @Suppress("UNUSED_PARAMETER")
        fun create(
            environment: IModEnvironment,
            configService: ConfigService,
            config: BingoConfig,
            clientSettings: ClientSettingsService,
            soundService: SoundService,
            text: TextProvider,
            eventBus: IEventBus,
            drawServiceFactory: IDrawServiceFactory,
            cardPlacementScreenFactory: BingoCardPlacementScreen.Factory,
            client: IClient,
        ): YetAnotherConfigLibIntegration {
            return YetAnotherConfigLibIntegrationDummy()
        }
    }
}

internal class YetAnotherConfigLibIntegrationDummy : YetAnotherConfigLibIntegration {
    override fun isInstalled(): Boolean = false
    override fun buildConfigScreen(parent: Screen): Screen? = null
}
