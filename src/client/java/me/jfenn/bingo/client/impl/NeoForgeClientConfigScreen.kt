package me.jfenn.bingo.client.impl

import me.jfenn.bingo.client.common.config.ClientServerConfigController
import me.jfenn.bingo.client.common.config.ClientServerConfigState
import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.config.NeoForgeConfigBridge
import me.jfenn.bingo.common.config.ServerConfigSnapshotPacket
import me.jfenn.bingo.common.config.ServerConfigUpdatePacket
import me.jfenn.bingo.platform.scope.BingoKoin
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.options.OptionsSubScreen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.neoforged.fml.ModContainer
import net.neoforged.fml.config.ModConfig
import net.neoforged.fml.config.ModConfigs
import net.neoforged.neoforge.client.gui.ConfigurationScreen
import net.neoforged.neoforge.client.gui.IConfigScreenFactory
import net.neoforged.neoforge.common.ModConfigSpec
import org.slf4j.LoggerFactory
import java.util.Locale

object NeoForgeClientConfigScreen {
    @JvmStatic
    fun register(modContainer: ModContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory::class.java) {
            IConfigScreenFactory { container, parent -> ExBingoConfigurationScreen(container, parent) }
        }
    }
}

private class ExBingoConfigurationScreen(
    private val mod: ModContainer,
    parent: Screen,
) : OptionsSubScreen(
    parent,
    Minecraft.getInstance().options,
    Component.translatable(
        "exbingo.configuration.title",
        mod.modInfo.displayName,
    ),
) {
    private var observedServerConfigRevision = Long.MIN_VALUE
    private var lastRemoteRequestMillis = 0L

    override fun init() {
        super.init()
        requestRemoteConfig(force = true)
    }

    override fun addOptions() {
        rebuildOptions()
    }

    private fun rebuildOptions() {
        val optionList = list ?: return
        OptionsListAccess.clear(optionList)

        for (type in ModConfig.Type.values()) {
            var headerAdded = false
            for (modConfig in ModConfigs.getConfigSet(type)) {
                if (modConfig.modId != mod.modId) continue

                if (!headerAdded) {
                    optionList.addSmall(
                        StringWidget(
                            ConfigurationScreen.BIG_BUTTON_WIDTH,
                            Button.DEFAULT_HEIGHT,
                            Component.translatable("$LANG_PREFIX${type.name.lowercase(Locale.ENGLISH)}")
                                .withStyle(ChatFormatting.UNDERLINE),
                            font,
                        ).alignLeft(),
                        null,
                    )
                    headerAdded = true
                }

                optionList.addSmall(createSectionButton(type, modConfig), null)
            }
        }
    }

    private fun createSectionButton(type: ModConfig.Type, modConfig: ModConfig): Button {
        val label = Component.translatable(
            SECTION,
            translatableConfig(modConfig, "", "${LANG_PREFIX}type.${modConfig.type.name.lowercase(Locale.ROOT)}"),
        )
        val title = translatableConfig(
            modConfig,
            ".title",
            "${LANG_PREFIX}title.${modConfig.type.name.lowercase(Locale.ROOT)}",
        )
        val button = Button.builder(label) {
            minecraft?.setScreen(createSectionScreen(type, modConfig, title))
        }.width(ConfigurationScreen.BIG_BUTTON_WIDTH).build()

        val tooltip = Component.empty()
        val spec = modConfig.spec as ModConfigSpec
        val remoteSnapshot = ClientServerConfigState.snapshot
        when {
            !spec.isLoaded -> {
                tooltip.append(ConfigurationScreen.TOOLTIP_CANNOT_EDIT_NOT_LOADED).append(EMPTY_LINE)
                button.active = false
            }

            isRemoteCommonConfig(type, modConfig) && remoteSnapshot == null -> {
                tooltip.append(Component.translatable("exbingo.configuration.server_config.loading").withStyle(ChatFormatting.YELLOW))
                    .append(EMPTY_LINE)
                button.active = false
                requestRemoteConfig()
            }

            isRemoteCommonConfig(type, modConfig) && remoteSnapshot?.canEdit != true -> {
                tooltip.append(Component.translatable("exbingo.configuration.server_config.requires_op").withStyle(ChatFormatting.RED))
                    .append(EMPTY_LINE)
                button.active = false
            }

            isRemoteCommonConfig(type, modConfig) -> {
                tooltip.append(Component.translatable("exbingo.configuration.server_config.remote").withStyle(ChatFormatting.GRAY))
                    .append(EMPTY_LINE)
            }

            type == ModConfig.Type.SERVER && Minecraft.getInstance().currentServer != null && !Minecraft.getInstance().isSingleplayer -> {
                tooltip.append(ConfigurationScreen.TOOLTIP_CANNOT_EDIT_THIS_WHILE_ONLINE).append(EMPTY_LINE)
                button.active = false
            }

            type == ModConfig.Type.SERVER && Minecraft.getInstance().hasSingleplayerServer() &&
                Minecraft.getInstance().singleplayerServer?.isPublished == true -> {
                tooltip.append(ConfigurationScreen.TOOLTIP_CANNOT_EDIT_THIS_WHILE_OPEN_TO_LAN).append(EMPTY_LINE)
                button.active = false
            }
        }

        tooltip.append(Component.translatable(FILENAME_TOOLTIP, modConfig.fileName).withStyle(FILENAME_TOOLTIP_STYLE))
        button.tooltip = Tooltip.create(tooltip)
        return button
    }

    private fun createSectionScreen(type: ModConfig.Type, modConfig: ModConfig, title: Component): Screen {
        val snapshot = ClientServerConfigState.snapshot
        if (isRemoteCommonConfig(type, modConfig) && snapshot?.canEdit == true) {
            val localCommonValues = NeoForgeConfigBridge.snapshotCommonValues()
            NeoForgeConfigBridge.setCommonValuesFrom(snapshot.config)
            return RemoteCommonConfigSectionScreen(
                this,
                type,
                modConfig,
                title,
                snapshot,
                localCommonValues,
            )
        }

        return ConfigurationScreen.ConfigurationSectionScreen(this, type, modConfig, title)
    }

    private fun translatableConfig(modConfig: ModConfig, suffix: String, fallback: String): MutableComponent {
        val fileKey = modConfig.fileName
            .replace(Regex("[^a-zA-Z0-9]+"), ".")
            .replace(Regex("^\\."), "")
            .replace(Regex("\\.$"), "")
            .lowercase(Locale.ENGLISH)
        return Component.translatableWithFallback(
            "${mod.modId}.configuration.section.$fileKey$suffix",
            Component.translatable(fallback, mod.modInfo.displayName).string,
            mod.modInfo.displayName,
        )
    }

    private fun requestRemoteConfig(force: Boolean = false) {
        if (!isRemoteServer()) return

        val now = System.currentTimeMillis()
        if (!force && now - lastRemoteRequestMillis < 1000L) return
        lastRemoteRequestMillis = now

        runCatching {
            BingoKoin.koinApp.koin.get<ClientServerConfigController>().requestSnapshot()
        }.onFailure {
            log.debug("[NeoForgeClientConfigScreen] Unable to request server config snapshot", it)
        }
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (isRemoteServer()) {
            requestRemoteConfig()
            val revision = ClientServerConfigState.revision
            if (revision != observedServerConfigRevision) {
                observedServerConfigRevision = revision
                rebuildOptions()
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick)
    }

    override fun onClose() {
        observedServerConfigRevision = ClientServerConfigState.revision
        super.onClose()
    }

    companion object {
        private const val LANG_PREFIX = "neoforge.configuration.uitext."
        private const val SECTION = "${LANG_PREFIX}section"
        private const val FILENAME_TOOLTIP = "${LANG_PREFIX}filenametooltip"
        private val FILENAME_TOOLTIP_STYLE = ChatFormatting.GRAY
        private val EMPTY_LINE = Component.literal("\n\n")
        private val log = LoggerFactory.getLogger("ExBingo")
    }
}

private class RemoteCommonConfigSectionScreen(
    parent: Screen,
    type: ModConfig.Type,
    modConfig: ModConfig,
    title: Component,
    private val snapshot: ServerConfigSnapshotPacket,
    private val localCommonValues: BingoConfig,
) : ConfigurationScreen.ConfigurationSectionScreen(parent, type, modConfig, title) {
    private var closing = false

    override fun onClose() {
        if (closing) return
        closing = true

        list?.applyUnsavedChanges()
        try {
            if (changed) {
                val updated = NeoForgeConfigBridge.applyCommonValuesTo(snapshot.config)
                sendRemoteUpdate(updated)
            }
        } finally {
            NeoForgeConfigBridge.setCommonValuesFrom(localCommonValues)
        }

        minecraft?.setScreen(lastScreen)
    }

    private fun sendRemoteUpdate(config: BingoConfig) {
        runCatching {
            BingoKoin.koinApp.koin.get<ClientServerConfigController>()
                .sendUpdate(ServerConfigUpdatePacket(config))
            ClientServerConfigState.update(ServerConfigSnapshotPacket(config, canEdit = true))
        }.onFailure {
            log.warn("[NeoForgeClientConfigScreen] Unable to send server config update", it)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger("ExBingo")
    }
}

private fun isRemoteCommonConfig(type: ModConfig.Type, modConfig: ModConfig): Boolean =
    type == ModConfig.Type.COMMON &&
        modConfig.fileName == NeoForgeConfigBridge.COMMON_FILE_NAME &&
        isRemoteServer()

private fun isRemoteServer(): Boolean {
    val minecraft = Minecraft.getInstance()
    return minecraft.currentServer != null && !minecraft.isSingleplayer
}
