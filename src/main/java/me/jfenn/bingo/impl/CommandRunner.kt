package me.jfenn.bingo.impl

import me.jfenn.bingo.platform.ICommandRunner
import net.minecraft.server.MinecraftServer

class CommandRunner : ICommandRunner {
    override fun runSilentCommand(server: MinecraftServer, cmd: String) {
        server.commands.performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd)
    }
}
