package me.jfenn.bingo.impl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

class ServerWorldImplChunkTest {
    private val directExecutor = Executor { it.run() }

    @Test
    fun `loadChunkFutureWithRegionTicket runs distance manager updates after addTicket and before getChunkFuture`() {
        val calls = mutableListOf<String>()

        val future = loadChunkFutureWithRegionTicket(
            addTicket = { calls += "add" },
            runDistanceManagerUpdates = { calls += "update" },
            getChunkFuture = {
                calls += "future"
                CompletableFuture.completedFuture("chunk")
            },
            removeTicket = { calls += "remove" },
            taskExecutor = directExecutor,
        )

        assertEquals(listOf("add", "update", "future", "remove"), calls)
        assertTrue(future.isDone)
        assertEquals("chunk", future.join())
    }

    @Test
    fun `loadChunkFutureWithRegionTicket removes ticket when distance manager update fails`() {
        val calls = mutableListOf<String>()

        assertFailsWith<IllegalStateException> {
            loadChunkFutureWithRegionTicket(
                addTicket = { calls += "add" },
                runDistanceManagerUpdates = {
                    calls += "update"
                    throw IllegalStateException("boom")
                },
                getChunkFuture = {
                    calls += "future"
                    CompletableFuture.completedFuture("chunk")
                },
                removeTicket = { calls += "remove" },
                taskExecutor = directExecutor,
            )
        }

        assertEquals(listOf("add", "update", "remove"), calls)
    }
}
