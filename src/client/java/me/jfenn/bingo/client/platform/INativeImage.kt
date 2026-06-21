package me.jfenn.bingo.client.platform

import net.minecraft.resources.ResourceLocation

interface INativeImageFactory {
    fun create(width: Int, height: Int): INativeImage
}

interface INativeImage: AutoCloseable {
    val textureId: ResourceLocation
    val width: Int
    val height: Int
    fun setPixel(x: Int, y: Int, color: Int)
    fun upload()
}
