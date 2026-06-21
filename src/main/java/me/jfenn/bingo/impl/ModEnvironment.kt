package me.jfenn.bingo.impl

import me.jfenn.bingo.platform.IModEnvironment
import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Path

class ModEnvironment : IModEnvironment {
    override val configDir: Path
        get() = FMLPaths.CONFIGDIR.get()

    override val gameDir: Path
        get() = FMLPaths.GAMEDIR.get()

    override val envType: IModEnvironment.EnvType
        get() = when (FMLEnvironment.dist) {
            Dist.CLIENT -> IModEnvironment.EnvType.CLIENT
            Dist.DEDICATED_SERVER -> IModEnvironment.EnvType.SERVER
        }

    override fun isModLoaded(modId: String): Boolean =
        ModList.get().isLoaded(modId)
}
