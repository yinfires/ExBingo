package me.jfenn.bingo.mixinhandler

import net.minecraft.world.level.DataPackConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class DedicatedServerPackConfigDefaultsTest {
    @Test
    fun `adds the bundle feature pack to dedicated server initial pack config`() {
        val config = DataPackConfig(listOf("vanilla"), emptyList())

        val adjusted = DedicatedServerPackConfigDefaults.forceBundleFeaturePack(config)

        assertEquals(listOf("vanilla", "bundle"), adjusted.enabled)
        assertEquals(emptyList(), adjusted.disabled)
        assertEquals(listOf("vanilla"), config.enabled)
    }

    @Test
    fun `does not duplicate bundle when it is already enabled`() {
        val config = DataPackConfig(listOf("vanilla", "bundle"), emptyList())

        val adjusted = DedicatedServerPackConfigDefaults.forceBundleFeaturePack(config)

        assertEquals(listOf("vanilla", "bundle"), adjusted.enabled)
    }

    @Test
    fun `removes bundle from disabled packs when server properties explicitly disable it`() {
        val config = DataPackConfig(listOf("vanilla"), listOf("bundle"))

        val adjusted = DedicatedServerPackConfigDefaults.forceBundleFeaturePack(config)

        assertEquals(listOf("vanilla", "bundle"), adjusted.enabled)
        assertEquals(emptyList(), adjusted.disabled)
    }
}
