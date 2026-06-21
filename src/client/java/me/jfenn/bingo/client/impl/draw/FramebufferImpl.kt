package me.jfenn.bingo.client.impl.draw

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.blaze3d.vertex.VertexSorting
import me.jfenn.bingo.client.mixinhelper.FramebufferOverride
import me.jfenn.bingo.client.platform.renderer.IDrawService
import me.jfenn.bingo.client.platform.renderer.IFramebuffer
import me.jfenn.bingo.common.MOD_ID_BINGO
import net.minecraft.client.Minecraft
import com.mojang.blaze3d.pipeline.TextureTarget
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.*
import net.minecraft.resources.ResourceLocation
import org.joml.Matrix4f
import java.util.*

class FramebufferImpl(
    private val fb: TextureTarget,
    private val service: DrawService,
) : IFramebuffer {

    private val client = Minecraft.getInstance()
    private val fbTexture = FramebufferTexture(fb)
    private val fbTextureId = ResourceLocation.fromNamespaceAndPath(MOD_ID_BINGO, "framebuffer/" + UUID.randomUUID().toString())

    override val width: Int
        get() = fb.width

    override val height: Int
        get() = fb.height

    override fun register() {
        client.textureManager.register(fbTextureId, fbTexture)
    }

    override fun resize(width: Int, height: Int) {
        fb.resize(width, height, Minecraft.ON_OSX)
    }

    private var backupProjMat = Matrix4f()
    private var backupVertexSorter = VertexSorting.ORTHOGRAPHIC_Z

    override fun write(callback: (IDrawService) -> Unit) {
        val context: GuiGraphics = service.context
        context.flush()

        fb.setClearColor(0f, 0f, 0f, 0f)
        fb.clear(Minecraft.ON_OSX)

        val window = Minecraft.getInstance().window

        val right = (fb.width.toDouble() / window.guiScale).toFloat()
        val bottom = (fb.height.toDouble() / window.guiScale).toFloat()
        val matrix4f = Matrix4f().setOrtho(
            0f,
            right,
            bottom,
            0f,
            1000f,
            21000f
        )

        backupProjMat = RenderSystem.getProjectionMatrix()
        backupVertexSorter = RenderSystem.getVertexSorting()
        RenderSystem.setProjectionMatrix(matrix4f, VertexSorting.ORTHOGRAPHIC_Z)

        RenderSystem.getModelViewStack()
            .pushMatrix()
            .translation(0f, 0f, -11000.0f)

        Lighting.setupFor3DItems()

        fb.bindWrite(true)
        FramebufferOverride.framebuffer = fb

        service.matrices.push()

        callback(service)

        context.flush()

        fb.unbindWrite()

        service.matrices.pop()

        FramebufferOverride.framebuffer = null
        client.mainRenderTarget.bindWrite(true)

        RenderSystem.getModelViewStack().popMatrix()
        RenderSystem.setProjectionMatrix(backupProjMat, backupVertexSorter)
    }

    override fun draw(service: IDrawService, width: Int, height: Int) {
        val context: GuiGraphics = service.context
        context.pose().pushPose()

        val scaleFactor = fb.width.toFloat() / width
        context.pose().scale(1f/scaleFactor, 1f/scaleFactor, 1f)

        RenderSystem.setShaderTexture(0, fbTextureId)
        RenderSystem.setShader { GameRenderer.getPositionTexShader() }

        val matrix4f = context.pose().last().pose()
        val bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)

        // The framebuffer renders upside down, so we need to flip v1 and v2 here
        // otherwise, this could just be a context.drawTexture call...
        val x1 = 0f
        val x2 = width * scaleFactor
        val y1 = 0f
        val y2 = height * scaleFactor
        val u1 = 0f
        val u2 = 1f
        val v1 = 1f
        val v2 = 0f

        bufferBuilder.addVertex(matrix4f, x1, y1, 0.0f).setUv(u1, v1)
        bufferBuilder.addVertex(matrix4f, x1, y2, 0.0f).setUv(u1, v2)
        bufferBuilder.addVertex(matrix4f, x2, y2, 0.0f).setUv(u2, v2)
        bufferBuilder.addVertex(matrix4f, x2, y1, 0.0f).setUv(u2, v1)

        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow())

        context.pose().popPose()
    }

    override fun close() {
        fb.destroyBuffers()
        client.textureManager.release(fbTextureId)
    }
}
