package me.jfenn.bingo.mixinhandler

import net.minecraft.server.packs.repository.PackRepository
import net.minecraft.world.level.DataPackConfig
import net.minecraft.world.level.WorldDataConfiguration
import net.minecraft.world.flag.FeatureFlagSet
import net.minecraft.world.flag.FeatureFlags

object DedicatedServerPackConfigDefaults {
    private const val BUNDLE_FEATURE_PACK_ID = "bundle"

    @JvmStatic
    fun forceBundleFeaturePack(config: DataPackConfig): DataPackConfig {
        val enabled = config.enabled.filterNot { it == BUNDLE_FEATURE_PACK_ID } + BUNDLE_FEATURE_PACK_ID
        val disabled = config.disabled.filterNot { it == BUNDLE_FEATURE_PACK_ID }
        return DataPackConfig(enabled, disabled)
    }

    @JvmStatic
    fun forceBundleFeaturePack(config: WorldDataConfiguration): WorldDataConfiguration {
        return WorldDataConfiguration(
            forceBundleFeaturePack(config.dataPacks),
            config.enabledFeatures.join(FeatureFlagSet.of(FeatureFlags.BUNDLE)),
        )
    }

    @JvmStatic
    fun forceBundleFeaturePack(packRepository: PackRepository, config: WorldDataConfiguration): WorldDataConfiguration {
        if (packRepository.isAvailable(BUNDLE_FEATURE_PACK_ID)) {
            packRepository.addPack(BUNDLE_FEATURE_PACK_ID)
        }

        return forceBundleFeaturePack(config)
    }
}
