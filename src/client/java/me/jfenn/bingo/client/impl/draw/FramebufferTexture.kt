package me.jfenn.bingo.client.impl.draw

import com.mojang.blaze3d.pipeline.RenderTarget
import net.minecraft.client.renderer.texture.AbstractTexture
import net.minecraft.server.packs.resources.ResourceManager

class FramebufferTexture(private val fb: RenderTarget) : AbstractTexture() {
    override fun getId(): Int {
        return fb.colorTextureId
    }

    override fun releaseId() {
        // no-op
    }

    override fun load(manager: ResourceManager) {
        // no-op
    }

    override fun close() {
        // no-op
    }
}
