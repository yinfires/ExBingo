package me.jfenn.bingo.client.impl.draw

import me.jfenn.bingo.client.platform.renderer.IMatrixStack
import com.mojang.blaze3d.vertex.PoseStack
import org.joml.AxisAngle4f
import org.joml.Quaternionf

class MatrixStackImpl(
    private val matrices: PoseStack,
) : IMatrixStack {
    override fun translate(x: Float, y: Float, z: Float) = matrices.translate(x, y, z)
    override fun scale(x: Float, y: Float, z: Float) = matrices.scale(x, y, z)
    override fun rotate(angle: Float) = matrices.mulPose(Quaternionf(AxisAngle4f(angle, 0f, 0f, 1f)))
    override fun push() = matrices.pushPose()
    override fun pop() = matrices.popPose()
}
