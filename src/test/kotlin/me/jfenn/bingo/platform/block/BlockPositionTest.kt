package me.jfenn.bingo.platform.block

import kotlin.test.Test
import kotlin.test.assertEquals

class BlockPositionTest {
    @Test
    fun `chunk position uses x and z coordinates`() {
        assertEquals(Pair(2, -4), BlockPosition(32, 91, -49).toChunkPos())
    }

    @Test
    fun `chunk position floors negative block coordinates`() {
        assertEquals(Pair(-1, -1), BlockPosition(-1, 64, -1).toChunkPos())
    }
}
