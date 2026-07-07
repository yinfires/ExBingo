package me.jfenn.bingo.client.mixinhandler

import me.jfenn.bingo.client.common.hud.ItemDifficultyOverlayService
import me.jfenn.bingo.common.card.tierlist.TierLabel
import me.jfenn.bingo.platform.scope.BingoKoin
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.item.ItemStack
import org.koin.core.component.get

object ItemDifficultyOverlayRenderer {
    private const val OVERLAY_Z = 180f
    private val itemDecorationContext = ThreadLocal<ItemDecorationContext?>()
    private val advancementContext = ThreadLocal<String?>()

    fun renderItemDecoration(guiGraphics: GuiGraphics, font: Font, stack: ItemStack, x: Int, y: Int) {
        val context = itemDecorationContext.get()
        val service = runCatching {
            BingoKoin.koinApp.koin.get<ItemDifficultyOverlayService>()
        }.getOrNull() ?: return
        val tier = when {
            context != null && service.isEnabled() -> context.tier
            context != null -> null
            else -> service.getItemTier(stack)
        } ?: return

        renderTier(guiGraphics, font, tier, x, y)
    }

    fun renderAdvancementIcon(guiGraphics: GuiGraphics, x: Int, y: Int) {
        val advancementId = advancementContext.get() ?: return
        renderAdvancementIcon(guiGraphics, advancementId, x, y)
    }

    fun renderAdvancementIcon(guiGraphics: GuiGraphics, advancementId: String, x: Int, y: Int) {
        val service = runCatching {
            BingoKoin.koinApp.koin.get<ItemDifficultyOverlayService>()
        }.getOrNull() ?: return
        val tier = service.getAdvancementTier(advancementId) ?: return

        renderTier(guiGraphics, Minecraft.getInstance().font, tier, x, y)
    }

    fun beginAdvancementIcon(advancementId: String) {
        advancementContext.set(advancementId)
    }

    fun endAdvancementIcon() {
        advancementContext.remove()
    }

    fun <T> withItemDecorationTier(tier: TierLabel?, block: () -> T): T {
        val previous = itemDecorationContext.get()
        itemDecorationContext.set(ItemDecorationContext(tier))
        try {
            return block()
        } finally {
            if (previous != null) {
                itemDecorationContext.set(previous)
            } else {
                itemDecorationContext.remove()
            }
        }
    }

    private fun renderTier(guiGraphics: GuiGraphics, font: Font, tier: TierLabel, x: Int, y: Int) {
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(0f, 0f, OVERLAY_Z)
        guiGraphics.drawString(
            font,
            tier.name,
            x + 1,
            y + 7,
            tier.formatting.color ?: 0xFFFFFF,
            true,
        )
        guiGraphics.pose().popPose()
    }

    private data class ItemDecorationContext(
        val tier: TierLabel?,
    )
}
