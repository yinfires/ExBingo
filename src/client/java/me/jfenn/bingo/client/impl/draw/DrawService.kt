package me.jfenn.bingo.client.impl.draw

import com.mojang.blaze3d.systems.RenderSystem
import me.jfenn.bingo.client.impl.NativeImageImpl
import me.jfenn.bingo.client.platform.INativeImage
import me.jfenn.bingo.client.platform.renderer.*
import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.impl.ItemStackFactory
import me.jfenn.bingo.platform.item.IItemStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import com.mojang.blaze3d.pipeline.TextureTarget
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.RenderBuffers
import net.minecraft.client.renderer.RenderType
import net.minecraft.world.item.ItemStack
import net.minecraft.resources.ResourceLocation
import org.joml.Vector2i

class DrawService(
    override val context: GuiGraphics,
) : IDrawService {

    override val textRenderer: Font = Minecraft.getInstance().font
    override val font: IFont = FontImpl(textRenderer)
    override val matrices: IMatrixStack = MatrixStackImpl(context.pose())
    override val window: IWindow = Companion

    override var delta: Float = 0.5f
    override val mouse: Vector2i
        get() {
            val client = Minecraft.getInstance()
            val mouse = client.mouseHandler
            val window = client.window
            return Vector2i(
                (mouse.xpos() * window.guiScaledWidth.toDouble() / window.width.toDouble()).toInt(),
                (mouse.ypos() * window.guiScaledHeight.toDouble() / window.height.toDouble()).toInt(),
            )
        }

    companion object : IDrawServiceFactory, IWindow {
        override val window: IWindow = this

        override val scaleFactor: Double get() = Minecraft.getInstance().window.guiScale
        override val scaledWindowWidth: Int
            get() = Minecraft.getInstance().window.guiScaledWidth
        override val scaledWindowHeight: Int
            get() = Minecraft.getInstance().window.guiScaledHeight

        override fun create(drawContext: GuiGraphics): IDrawService = DrawService(drawContext)

        private val bufferBuilderStorage by lazy {
            RenderBuffers(Runtime.getRuntime().availableProcessors())
        }

        // 棋盘格子直接渲染,不走离屏 framebuffer 缓存。
        //
        // 离屏 framebuffer 路径(newBuffer/FramebufferImpl)会把整张棋盘预渲染成一张纹理缓存,
        // 但它重建的投影/深度/渲染状态无法正确渲染**需要走完整模型管线的物品**:
        //   - BEWLR 特殊渲染物品(头颅、奖杯如 twilightforest:knight_phantom_trophy、盾牌、旗帜、潜影盒)
        //   - 多层 / 自发光(emissive)模型方块(如 twilightforest:carminite_block 砷铅铁块)
        // 这些物品在离屏路径里会渲染为透明。已实机验证:关闭离屏缓存、走直接渲染后全部正常。
        //
        // 棋盘格子数量有限(25 格 × 少数队伍),直接渲染的每帧开销在现代机器上可忽略,
        // 正确性优先于这层缓存。framebuffer 实现保留在 newBuffer/FramebufferImpl,
        // 若将来修好其投影/深度/状态重建,可改回 true 重新启用缓存。
        override val isBufferSupported: Boolean = false

        override fun newBuffer(width: Int, height: Int): IFramebuffer {
            val client = Minecraft.getInstance()
            val buffer = TextureTarget(width, height, true, Minecraft.ON_OSX)
            val context = GuiGraphics(client, bufferBuilderStorage.bufferSource())

            return FramebufferImpl(
                buffer, DrawService(context)
            )
        }
    }

    override fun draw() {
        context.flush()
    }

    override fun fill(x1: Int, y1: Int, x2: Int, y2: Int, color: Int) {
        context.fill(x1, y1, x2, y2, color)
    }

    override fun overlayFill(x1: Int, y1: Int, x2: Int, y2: Int, color: Int) {
        context.fill(RenderType.guiOverlay(), x1, y1, x2, y2, color)
    }

    override fun drawHorizontalLine(x1: Int, x2: Int, y: Int, color: Int) {
        context.hLine(x1, x2, y, color)
    }

    override fun drawVerticalLine(x: Int, y1: Int, y2: Int, color: Int) {
        context.vLine(x, y1, y2, color)
    }

    override fun enableBlend() {
        RenderSystem.enableBlend()
    }

    override fun disableBlend() {
        RenderSystem.disableBlend()
    }

    override fun setShaderColor(r: Float, g: Float, b: Float, a: Float) {
        context.setColor(r, g, b, a)
    }

    override fun drawText(text: IText, x: Int, y: Int, color: Int, shadow: Boolean) {
        context.drawString(textRenderer, text.value, x, y, color, shadow)
    }

    override fun drawItemStack(stack: IItemStack, x: Int, y: Int, seed: Int) {
        require(stack is ItemStackFactory.ItemStackImpl)
        val itemStack: ItemStack = stack.stack
        context.renderItem(itemStack, x, y, seed)

        // draws the item count + durability bar
        val textRenderer = Minecraft.getInstance().font
        context.renderItemDecorations(textRenderer, stack.stack, x, y)
    }

    override fun drawTooltip(text: List<IText>, x: Int, y: Int) {
        val textRenderer = Minecraft.getInstance().font
        val textList = text.map { it.value.visualOrderText }.toMutableList()
        context.renderTooltip(textRenderer, textList, x, y)
    }

    override fun drawDynamicTexture(texture: INativeImage, x: Int, y: Int, width: Int, height: Int) {
        require(texture is NativeImageImpl)
        context.blit(texture.textureId, x, y, 0, 0f, 0f, width, height, texture.width, texture.height)
    }

    override fun drawGuiTexture(
        texture: ResourceLocation,
        x: Int,
        y: Int,
        z: Int,
        u: Float,
        v: Float,
        width: Int,
        height: Int,
        textureWidth: Int,
        textureHeight: Int
    ) {
        context.blit(
            with(texture) { ResourceLocation.fromNamespaceAndPath(namespace, "textures/gui/sprites/$path.png") },
            x, y, z,
            u, v,
            width, height,
            textureWidth, textureHeight
        )
    }

}
