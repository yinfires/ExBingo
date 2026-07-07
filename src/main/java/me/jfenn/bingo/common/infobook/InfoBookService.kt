package me.jfenn.bingo.common.infobook

import me.jfenn.bingo.platform.text.HoverAction
import me.jfenn.bingo.platform.text.ITextFactory
import me.jfenn.bingo.platform.text.TextAction
import me.jfenn.bingo.common.*
import me.jfenn.bingo.common.commands.BingoPrefsCommand
import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.config.PlayerSettings
import me.jfenn.bingo.common.config.PlayerSettingsService
import me.jfenn.bingo.common.map.CardViewService
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.BINGO_VERSION
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.integrations.permissions.IPermissionsApi
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.item.IItemStackFactory
import me.jfenn.bingo.platform.item.IWrittenBook
import me.jfenn.bingo.platform.scoreboard.IScoreboardManager
import net.minecraft.ChatFormatting

internal class InfoBookService(
    private val config: BingoConfig,
    private val state: BingoState,
    private val text: TextProvider,
    private val permissions: IPermissionsApi,
    private val cardViewService: CardViewService,
    private val scoreboardManager: IScoreboardManager,
    private val playerSettingsService: PlayerSettingsService,
    private val itemStackFactory: IItemStackFactory,
    private val textFactory: ITextFactory,
) {

    private fun commandLink(label: String, suggestion: String = label) =
        textFactory.literal(label)
            .formatted(ChatFormatting.DARK_GREEN, ChatFormatting.UNDERLINE)
            .also { it.setClickEvent(TextAction.SuggestCommand(suggestion)) }

    private fun getIntroPage(player: IPlayerHandle) =
        textFactory.empty()
            .append(
                text.string(
                    StringKey.IntroWelcome,
                    text.string(StringKey.IntroWelcomeLink).formatted(ChatFormatting.DARK_GREEN, ChatFormatting.UNDERLINE).also {
                        it.setClickEvent(TextAction.OpenUrl(URL_EXBINGO))
                    },
                ).formatted(ChatFormatting.BOLD)
            )
            .append("\n")
            .append(textFactory.literal("v$BINGO_VERSION").formatted(ChatFormatting.GRAY))
            .append("\n\n")
            .append(
                text.string(
                    StringKey.IntroPortNote,
                    text.string(StringKey.IntroPortNoteLink).formatted(ChatFormatting.DARK_GREEN, ChatFormatting.UNDERLINE).also {
                        it.setClickEvent(TextAction.OpenUrl(URL_WIKI))
                    },
                ).formatted(ChatFormatting.GRAY)
            )
            .also {
                if (permissions.hasPermission(player, Permission.CONFIGURE_GAME)) {
                    it.append("\n\n").append(
                        when {
                            state.isLobbyMode -> text.string(StringKey.IntroUseConfigMenu)
                            else -> text.string(
                                StringKey.IntroUseConfigCommands,
                                commandLink("/bingo", "/bingo "),
                            )
                        }
                    )
                }
            }
            .append("\n\n")
            .append(
                text.string(
                    StringKey.IntroReadHowToPlay,
                    text.string(StringKey.IntroReadHowToPlayLink).formatted(ChatFormatting.DARK_GREEN, ChatFormatting.UNDERLINE).also {
                        it.setClickEvent(TextAction.OpenUrl(URL_WIKI_WHAT_IS_BINGO))
                    },
                )
            )
            .also {
                if (!permissions.hasPermission(player, Permission.CONFIGURE_GAME)) {
                    it.append("\n\n").append(text.string(StringKey.IntroChangePlayerSettings))
                }
            }
            .append("\n\n")
            .append(text.string(StringKey.IntroHaveFun))

    private fun getHintsPage(player: IPlayerHandle) =
        textFactory.empty()
            .append(text.string(StringKey.IntroHints).formatted(ChatFormatting.BOLD))
            .append("\n\n")
            .append(when {
                cardViewService.supportsCardHud(player) -> text.string(
                    StringKey.IntroKeybindHud,
                    textFactory.keybind(KEYBIND_OPEN_CARD).formatted(ChatFormatting.DARK_GREEN),
                )
                else -> text.string(
                    StringKey.IntroKeybindMap,
                    textFactory.keybind("key.use").formatted(ChatFormatting.DARK_GREEN),
                )
            })
            .also {
                if (permissions.hasPermission(player, Permission.COMMAND_COORDS)) {
                    it.append("\n\n").append(text.string(StringKey.IntroUseCoords, commandLink("/coords")))
                }
            }

    private fun getModCompatPage() =
        textFactory.empty()
            .append(text.string(StringKey.IntroAutotierTitle).formatted(ChatFormatting.BOLD))
            .append("\n\n")
            .append(text.string(StringKey.IntroAutotierIntro))
            .append("\n\n")
            .append(
                text.string(
                    StringKey.IntroAutotierCommand,
                    commandLink("/bingo autotier generate"),
                )
            )
            .append("\n\n")
            .append(text.string(StringKey.IntroAutotierNote).formatted(ChatFormatting.GRAY))

    private fun getCardDisablePage() =
        textFactory.empty()
            .append(text.string(StringKey.IntroCarddisableTitle).formatted(ChatFormatting.BOLD))
            .append("\n\n")
            .append(text.string(StringKey.IntroCarddisableIntro))
            .append("\n\n")
            .append(
                text.string(
                    StringKey.IntroCarddisable,
                    commandLink("/bingo carddisable <board>", "/bingo carddisable "),
                    commandLink("/bingo cardenable <board>", "/bingo cardenable "),
                )
            )
            .append("\n\n")
            .append(text.string(StringKey.IntroCarddisableNote).formatted(ChatFormatting.GRAY))

    private fun getTeamFeaturesPage(player: IPlayerHandle) =
        textFactory.empty()
            .append(text.string(StringKey.IntroTeamFeaturesTitle).formatted(ChatFormatting.BOLD))
            .append("\n\n")
            .append(
                text.string(
                    StringKey.IntroTeamFeaturesChest,
                    commandLink("/teamchest"),
                    commandLink("/tc"),
                )
            )
            .append("\n")
            .append(
                text.string(
                    StringKey.IntroTeamFeaturesChestKey,
                    textFactory.keybind(KEYBIND_OPEN_TEAM_CHEST).formatted(ChatFormatting.DARK_GREEN),
                )
            )
            .append("\n\n")
            .append(
                text.string(
                    StringKey.IntroTeamFeaturesTeleport,
                    commandLink("/teamtp <player>", "/teamtp "),
                    commandLink("/ttp <player>", "/ttp "),
                )
            )
            .also {
                if (permissions.hasPermission(player, Permission.CONFIGURE_GAME)) {
                    it.append("\n\n").append(
                        text.string(
                            StringKey.IntroTeamFeaturesOps,
                            commandLink("/teamchest toggle"),
                            commandLink("/teamchest count"),
                            commandLink("/tptoggle"),
                        ).formatted(ChatFormatting.GRAY)
                    )
                }
            }

    private fun toggleText(
        value: Boolean,
        name: StringKey,
        description: StringKey,
    ) =
        textFactory.empty()
            .append(
                textFactory.literal(if (value) "\u2611" else "\u2610")
                    .formatted(
                        if (value) ChatFormatting.DARK_GREEN else ChatFormatting.DARK_RED,
                    )
            )
            .append(" ")
            .append(text.string(name))
            .append(
                textFactory.literal(" \uD83D\uDEC8").formatted(ChatFormatting.GRAY).also {
                    it.setHoverEvent(HoverAction.ShowText(text.string(description)))
                }
            )

    private fun getSettingsPage(player: IPlayerHandle, settings: PlayerSettings) =
        textFactory.empty()
            .append(text.string(StringKey.PlayerSettings).formatted(ChatFormatting.BOLD))
            .append("\n\n")
            .append(
                toggleText(settings.bossbar, StringKey.PlayerSettingsBossbar, StringKey.PlayerSettingsBossbarDescription)
                    .also {
                        it.setClickEvent(TextAction.RunCommand(
                            BingoPrefsCommand.getCommand(PlayerSettings::bossbar, !settings.bossbar)
                        ))
                    }
            )
            .append("\n\n")
            .append(
                toggleText(settings.scoreboard, StringKey.PlayerSettingsScoreboard, StringKey.PlayerSettingsScoreboardDescription)
                    .also {
                        it.setClickEvent(TextAction.RunCommand(
                            BingoPrefsCommand.getCommand(PlayerSettings::scoreboard, !settings.scoreboard)
                        ))
                    }
            )
            .also {
                if (!cardViewService.supportsCardHud(player)) {
                    it.append("\n")
                    it.append(
                        toggleText(settings.scoreboardAutoHide, StringKey.PlayerSettingsScoreboardAutoHide, StringKey.PlayerSettingsScoreboardAutoHideDescription)
                            .also {
                                it.setClickEvent(TextAction.RunCommand(
                                    BingoPrefsCommand.getCommand(PlayerSettings::scoreboardAutoHide, !settings.scoreboardAutoHide)
                                ))
                            }
                    )
                }
            }
            .append("\n\n")
            .append(text.string(StringKey.PlayerSettingsMessages))
            .append(":\n")
            .append(
                toggleText(settings.leadingMessages, StringKey.PlayerSettingsMessagesLeading, StringKey.PlayerSettingsMessagesLeadingDescription)
                    .also {
                        it.setClickEvent(TextAction.RunCommand(
                            BingoPrefsCommand.getCommand(PlayerSettings::leadingMessages, !settings.leadingMessages)
                        ))
                    }
            )
            .append("\n")
            .append(
                toggleText(settings.scoreMessages, StringKey.PlayerSettingsMessagesLines, StringKey.PlayerSettingsMessagesLinesDescription)
                    .also {
                        it.setClickEvent(TextAction.RunCommand(
                            BingoPrefsCommand.getCommand(PlayerSettings::scoreMessages, !settings.scoreMessages)
                        ))
                    }
            )
            .append("\n")
            .append(
                toggleText(settings.itemMessages, StringKey.PlayerSettingsMessagesItems, StringKey.PlayerSettingsMessagesItemsDescription)
                    .also {
                        it.setClickEvent(TextAction.RunCommand(
                            BingoPrefsCommand.getCommand(PlayerSettings::itemMessages, !settings.itemMessages)
                        ))
                    }
            )
            .append("\n\n")
            .append(
                toggleText(settings.nightVision, StringKey.PlayerSettingsNightVision, StringKey.PlayerSettingsNightVisionDescription)
                    .also {
                        it.setClickEvent(TextAction.RunCommand(
                            BingoPrefsCommand.getCommand(PlayerSettings::nightVision, !settings.nightVision)
                        ))
                    }
            )

    private fun isBookItem(stack: IWrittenBook) =
        stack.hasCustomTag(NBT_BINGO_INFO_BOOK)

    private fun createBookItem(player: IPlayerHandle) : IWrittenBook {
        val stack = itemStackFactory.createWrittenBook()
        stack.addCustomTag(NBT_BINGO_INFO_BOOK)
        stack.addCustomTag(NBT_BINGO_IGNORE)
        stack.addCustomTag(NBT_BINGO_VANISH)

        if (state.isLobbyMode && state.state == GameState.PREGAME && config.lobbyTutorialBook)
            stack.addCustomTag(NBT_BINGO_KEEP)

        stack.title = text.raw(StringKey.FullName)
        stack.author = player.playerName
        updateBookItem(player, stack)
        return stack
    }

    private fun updateBookItem(player: IPlayerHandle, stack: IWrittenBook) {
        val settings = playerSettingsService.getPlayer(player)

        stack.setPages(buildList {
            if (settings.seenTutorial) {
                if (permissions.hasPermission(player, Permission.CONFIGURE_PLAYER))
                    add(getSettingsPage(player, settings))
                add(getIntroPage(player))
                add(getHintsPage(player))
            } else {
                add(getIntroPage(player))
                if (permissions.hasPermission(player, Permission.CONFIGURE_PLAYER))
                    add(getSettingsPage(player, settings))
                add(getHintsPage(player))
            }
            add(getTeamFeaturesPage(player))

            // mod compatibility & auto-tiering (ops only — it can write server config)
            if (permissions.hasPermission(player, Permission.COMMAND_AUTOTIER))
                add(getModCompatPage())

            // disabling boards (ops only — it writes server config)
            if (permissions.hasPermission(player, Permission.COMMAND_CARD_TOGGLE))
                add(getCardDisablePage())
        })
    }

    fun updateBookItem(player: IPlayerHandle) {
        player.allHeldStacks()
            .mapNotNull { it.asWrittenBook() }
            .filter { isBookItem(it) }
            .forEach { updateBookItem(player, it) }
    }

    fun giveBookItem(player: IPlayerHandle) {
        var bookCount = 0
        for (view in player.allHeldStackViews()) {
            val stack = view.stack.asWrittenBook() ?: continue
            if (isBookItem(stack)) {
                // prevent players from taking someone else's book...
                if (stack.author != scoreboardManager.getPlayerName(player))
                    updateBookItem(player, stack)

                bookCount++

                if (bookCount > 1)
                    view.mutate { it.count = 0 }
                else if (stack.count > 1)
                    view.mutate { it.count = 1 }
            }
        }

        if (bookCount == 0) {
            val bookItem = createBookItem(player)
            player.giveItemStack(bookItem)
        }
    }
}
