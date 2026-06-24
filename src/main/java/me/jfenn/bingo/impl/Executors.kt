package me.jfenn.bingo.impl

import me.jfenn.bingo.platform.IExecutors
import net.minecraft.server.MinecraftServer
import net.minecraft.server.TickTask
import net.minecraft.Util
import java.util.concurrent.ExecutorService

object Executors : IExecutors {
    override val main: ExecutorService = Util.backgroundExecutor()
    override val io: ExecutorService = Util.ioPool()

    override fun createServerTaskExecutor(server: MinecraftServer): IExecutors.IServerTaskExecutor {
        return ServerTaskExecutor(server)
    }
}

class ServerTaskExecutor(
    private val server: MinecraftServer
) : IExecutors.IServerTaskExecutor {
    override fun execute(runnable: Runnable) {
        // Use (ticks - 3) to run the task immediately (otherwise there's a 3 tick delay)
        server.tell(TickTask(server.tickCount - 3, runnable))
    }

    override fun executeNextTick(runnable: Runnable) {
        server.tell(TickTask(server.tickCount + 1, runnable))
    }
}
