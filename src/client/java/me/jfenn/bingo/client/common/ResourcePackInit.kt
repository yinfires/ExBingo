package me.jfenn.bingo.client.common

import me.jfenn.bingo.client.platform.IResourcePackManager

internal class ResourcePackInit(
    resourcePackManager: IResourcePackManager,
) {
    init {
        resourcePackManager.register("exbingo:classic")
        resourcePackManager.register("exbingo:futurist")
    }
}
