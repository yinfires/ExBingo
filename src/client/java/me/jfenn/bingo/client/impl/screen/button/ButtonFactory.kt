package me.jfenn.bingo.client.impl.screen.button

import me.jfenn.bingo.client.impl.draw.DrawService
import me.jfenn.bingo.client.platform.screen.IButton
import me.jfenn.bingo.client.platform.screen.IButtonFactory
import me.jfenn.bingo.common.utils.EventListener
import me.jfenn.bingo.impl.TextImpl
import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.utils.IEventListener
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.WidgetSprites
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.ImageButton
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.joml.Vector2i

class ButtonFactory : IButtonFactory {
    override fun createDefaultButton(
        message: IText,
        onClick: () -> Unit
    ) = Button.builder(message.value, { onClick() })
        .build()
        .let { TexturedButtonImpl(it) }

    override fun createButton(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        texture: String?,
        focusedTexture: String?,
        inactiveTexture: String?,
        onClick: (Button) -> Unit,
        message: IText
    ): IButton = ImageButton(
        x,
        y,
        width,
        height,
        WidgetSprites(
            texture?.let { ResourceLocation.parse(it) }, inactiveTexture?.let { ResourceLocation.parse(it) },
            focusedTexture?.let { ResourceLocation.parse(it) }, inactiveTexture?.let { ResourceLocation.parse(it) },
        ),
        onClick,
        message.value,
    ).let { TexturedButtonImpl(it) }

    override fun createNinePatchButton(
        sliceSize: Int,
        textureSize: Vector2i,
        texture: String?,
        focusedTexture: String?,
        inactiveTexture: String?,
    ): IButton {
        return NinePatchButton(sliceSize, textureSize, texture, focusedTexture, inactiveTexture)
    }
}

interface ButtonImpl : IButton {
    val button: AbstractWidget
}

class TexturedButtonImpl(
    override val button: AbstractWidget
) : ButtonImpl {
    override var position: Vector2i
        get() = Vector2i(button.x, button.y)
        set(value) {
            button.x = value.x
            button.y = value.y
        }

    override var size: Vector2i
        get() = Vector2i(button.width, button.height)
        set(value) {
            button.width = value.x
            button.height = value.y
        }

    override var message: IText
        get() = TextImpl(button.message.copy())
        set(value) {
            button.message = value.value
        }

    override var active: Boolean by button::active

    override val onClick: IEventListener<Unit> = EventListener()
}

class NinePatchButton(
    private val sliceSize: Int,
    private val textureSize: Vector2i,
    texture: String?,
    focusedTexture: String?,
    inactiveTexture: String?,
) : ButtonImpl {

    private val textures = WidgetSprites(
        texture?.let { ResourceLocation.parse(it) }, inactiveTexture?.let { ResourceLocation.parse(it) },
        focusedTexture?.let { ResourceLocation.parse(it) }, inactiveTexture?.let { ResourceLocation.parse(it) },
    )

    override val button = object : Button(
        0, 0, 0, 0,
        Component.empty(),
        { onClick.invoke(Unit) },
        Button.DEFAULT_NARRATION
    ) {
        override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, deltaTicks: Float) {
            val service = DrawService(context)
            val identifier = textures.get(this.isActive, this.isHoveredOrFocused)
            service.drawNinePatch(identifier, x, y, width, height, sliceSize, textureSize.x, textureSize.y)
            renderString(context, Minecraft.getInstance().font, -1)
        }
    }

    override var position: Vector2i
        get() = Vector2i(button.x, button.y)
        set(value) {
            button.x = value.x
            button.y = value.y
        }

    override var size: Vector2i
        get() = Vector2i(button.width, button.height)
        set(value) {
            button.width = value.x
            button.height = value.y
        }

    override var message: IText
        get() = TextImpl(button.message.copy())
        set(value) {
            button.message = value.value
        }

    override var active: Boolean by button::active

    override val onClick: IEventListener<Unit> = EventListener()
}
