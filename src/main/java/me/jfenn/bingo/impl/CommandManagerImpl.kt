package me.jfenn.bingo.impl

import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.commands.*
import me.jfenn.bingo.platform.scope.BingoKoin
import net.minecraft.Util
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.MessageArgument
import net.minecraft.commands.arguments.ComponentArgument
import net.minecraft.server.MinecraftServer
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.PlayerChatMessage
import net.minecraft.ChatFormatting
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import org.koin.core.scope.Scope
import org.slf4j.Logger
import java.util.concurrent.CompletableFuture

class CommandManagerImpl(
    private val log: Logger,
) : ICommandManager {

    private val root = CommandNode.Root()

    override fun register(configure: CommandBuilder.() -> Unit) {
        CommandBuilder(root).configure()
    }

    private fun constructCommand(
        command: CommandNode,
        registryAccess: CommandBuildContext,
    ): ArgumentBuilder<CommandSourceStack, *> {
        var builder: ArgumentBuilder<CommandSourceStack, *> = when (command) {
            is CommandNode.Literal -> LiteralArgumentBuilder.literal(command.name)
            is CommandNode.RequiredArgument<*> -> {
                when (val arg = command.arg) {
                    is CommandArgument.String -> RequiredArgumentBuilder.argument<CommandSourceStack?, String?>(
                        arg.name,
                        if (arg.greedy) StringArgumentType.greedyString() else StringArgumentType.string()
                    ).let {
                        when (val suggestions = arg.suggestions) {
                            null -> it
                            else -> it.suggests(SuggestionProviderImpl(arg.greedy, suggestions))
                        }
                    }

                    is CommandArgument.SignedMessage -> RequiredArgumentBuilder.argument(
                        arg.name,
                        MessageArgument.message()
                    )

                    is CommandArgument.Component -> RequiredArgumentBuilder.argument(
                        arg.name,
                        ComponentArgument.textComponent(registryAccess)
                    )

                    is CommandArgument.Bool -> RequiredArgumentBuilder.argument(
                        arg.name,
                        BoolArgumentType.bool()
                    )

                    is CommandArgument.Integer -> RequiredArgumentBuilder.argument(
                        arg.name,
                        IntegerArgumentType.integer(arg.min, arg.max)
                    )

                    is CommandArgument.NumberLong -> RequiredArgumentBuilder.argument(
                        arg.name,
                        LongArgumentType.longArg(arg.min, arg.max)
                    )

                    is CommandArgument.Player -> RequiredArgumentBuilder.argument(
                        arg.name,
                        EntityArgument.player()
                    )
                }
            }

            is CommandNode.Root -> throw IllegalArgumentException("Root nodes must not be provided within the tree!")
        }

        for (child in command.children) {
            val childCommand: ArgumentBuilder<CommandSourceStack, *> = constructCommand(child, registryAccess)

            @Suppress("UNCHECKED_CAST")
            builder = builder.then(childCommand) as ArgumentBuilder<CommandSourceStack, *>
        }

        if (command.requires != null) {
            builder.requires { source ->
                // Server is null when the command is being evaluated for datapack functions
                if (source.server == null)
                    return@requires command.isAvailableToDataPacks

                try {
                    command.requires?.invoke(ExecutionSourceImpl(source)) ?: true
                } catch (e: Throwable) {
                    log.error("Error in command predicate:", e)
                    false
                }
            }
        }

        if (command.callback != null) {
            builder.executes { ctx ->
                try {
                    command.callback?.invoke(ExecutionContextImpl(ctx))
                    1
                } catch (e: Throwable) {
                    if (e is CommandSyntaxException) throw e
                    log.error("Error in command handler:", e)
                    ctx.source.sendSystemMessage(Component.literal(e.message).withStyle(ChatFormatting.RED))
                    0
                }
            }
        }

        return builder
    }

    init {
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent::class.java) { event ->
            val dispatcher = event.dispatcher
            val registryAccess = event.buildContext
            for (command in root.children) {
                val builder = constructCommand(command, registryAccess) as? LiteralArgumentBuilder<CommandSourceStack>
                    ?: throw IllegalArgumentException("Commands on the root node must only be literal()!")

                dispatcher.register(builder)
            }
        }
    }
}

class SuggestionProviderImpl(
    private val isGreedy: Boolean,
    private val callback: IExecutionContext.(String) -> Iterable<String>,
) : SuggestionProvider<CommandSourceStack> {
    private fun String.quoteIfNecessary(): String {
        return if (!isGreedy && contains(Regex("[^A-Za-z0-9\\-_]")))
            "\"$this\""
        else this
    }

    override fun getSuggestions(
        context: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val ctx = ExecutionContextImpl(context)
        val suggestions = callback.invoke(ctx, builder.remaining).map { it.quoteIfNecessary() }
        return SharedSuggestionProvider.suggest(suggestions, builder)
    }
}

open class ExecutionSourceImpl(
    source: CommandSourceStack,
) : IExecutionSource {
    override val server: MinecraftServer = source.server
    override val scope: Scope = BingoKoin.getScope(source.server)
        ?: throw IllegalArgumentException("No scope registered for this server!")
    override val player: IPlayerHandle? = source.player?.let { PlayerHandle(it) }
    override val isConsole: Boolean = source.entity == null
    override fun error(text: IText): Nothing {
        throw SimpleCommandExceptionType(text.value).create()
    }
}

class ExecutionContextImpl(
    private val ctx: CommandContext<CommandSourceStack>,
) : ExecutionSourceImpl(ctx.source), IExecutionContext {
    override fun sendMessage(text: IText) {
        ctx.source.sendSystemMessage(text.value)
    }
    override fun sendFeedback(text: IText) {
        ctx.source.sendSuccess({ text.value }, true)
    }
    override fun <T> getArgument(arg: CommandArgument<T>): T {
        @Suppress("UNCHECKED_CAST")
        return when (arg) {
            is CommandArgument.String -> StringArgumentType.getString(ctx, arg.name) as T
            is CommandArgument.SignedMessage -> {
                val sender = ctx.source.player?.uuid ?: Util.NIL_UUID
                val raw = MessageArgument.getMessage(ctx, arg.name).string
                CompletableFuture.completedFuture<ISignedMessage>(
                    SignedMessageImpl(PlayerChatMessage.unsigned(sender, raw))
                ) as T
            }
            is CommandArgument.Component -> ComponentArgument.getComponent(ctx, arg.name) as T
            is CommandArgument.Bool -> BoolArgumentType.getBool(ctx, arg.name) as T
            is CommandArgument.Integer -> IntegerArgumentType.getInteger(ctx, arg.name) as T
            is CommandArgument.NumberLong -> LongArgumentType.getLong(ctx, arg.name) as T
            is CommandArgument.Player -> EntityArgument.getPlayer(ctx, arg.name)?.let { PlayerHandle(it) } as T
        }
    }
}
