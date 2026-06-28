package me.jfenn.bingo.integrations.xaero

import me.jfenn.bingo.platform.IModEnvironment

class XaeroMapApiFactory : IXaeroMapApiFactory {
    override fun create(environment: IModEnvironment): IXaeroMapApi? {
        // Only instantiate the real implementation (which links against Xaero's
        // classes) when the mod is actually present, so a server without Xaero
        // never triggers a NoClassDefFoundError.
        return when {
            environment.isModLoaded("xaerominimap") -> XaeroMapApi()
            else -> null
        }
    }
}
