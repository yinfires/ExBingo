package me.jfenn.bingo.integrations.jei

import com.mojang.blaze3d.platform.InputConstants
import me.jfenn.bingo.client.integrations.jei.IJeiApi
import mezz.jei.api.ingredients.ITypedIngredient
import mezz.jei.api.constants.VanillaTypes
import mezz.jei.api.runtime.IIngredientManager
import mezz.jei.api.runtime.IJeiKeyMapping
import mezz.jei.api.recipe.RecipeIngredientRole
import net.minecraft.world.item.ItemStack
import org.slf4j.LoggerFactory

class JeiApi : IJeiApi {
    private val log = LoggerFactory.getLogger("ExBingo")
    private var bookmarkReflectionFailed = false

    override fun openItemRecipe(stack: ItemStack): Boolean {
        val runtime = JeiEntrypoint.runtime ?: run {
            log.error("JEI Runtime not available!")
            return false
        }

        val focus = runtime.jeiHelpers.focusFactory.createFocus(
            RecipeIngredientRole.OUTPUT,
            VanillaTypes.ITEM_STACK,
            stack,
        )

        runtime.recipesGui.show(focus)
        return true
    }

    override fun handleHoveredStackKey(keyCode: Int, scanCode: Int, stack: ItemStack): Boolean {
        if (stack.isEmpty) return false

        val runtime = JeiEntrypoint.runtime ?: return false

        if (matchesKey(runtime.keyMappings.showRecipe, keyCode, scanCode)) {
            return openItemRecipe(stack)
        }

        if (matchesKey(runtime.keyMappings.showUses, keyCode, scanCode)) {
            return openItemUses(stack)
        }

        val bookmarkKey = getBookmarkKeyMapping() ?: return false
        if (matchesKey(bookmarkKey, keyCode, scanCode)) {
            // Card tiles carry a display stack with ExBingo's own custom name / lore /
            // custom-data components. JEI derives a bookmark's identity from the stack's
            // components, so bookmarking the display stack would store an entry that no
            // longer matches the vanilla item (and appears "missing" in the bookmark list).
            // Recipe/uses go through createFocus which normalizes internally, so they were
            // unaffected — the bookmark path is the only one that needs a clean stack.
            val cleanStack = ItemStack(stack.item, stack.count)
            return toggleBookmark(runtime.bookmarkOverlay, runtime.ingredientManager, cleanStack)
        }

        return false
    }

    override fun openItemUses(stack: ItemStack): Boolean {
        val runtime = JeiEntrypoint.runtime ?: run {
            log.error("JEI Runtime not available!")
            return false
        }

        val focus = runtime.jeiHelpers.focusFactory.createFocus(
            RecipeIngredientRole.INPUT,
            VanillaTypes.ITEM_STACK,
            stack,
        )

        runtime.recipesGui.show(focus)
        return true
    }

    // The bookmark key is not exposed by the public IJeiKeyMappings (which only has
    // showRecipe / showUses). It lives on JEI's internal IInternalKeyMappings, reachable
    // via the mezz.jei.common.Internal singleton — so we fetch it reflectively.
    private fun getBookmarkKeyMapping(): IJeiKeyMapping? {
        return runCatching {
            val internal = Class.forName("mezz.jei.common.Internal")
            val keyMappings = internal.getMethod("getKeyMappings").invoke(null)
            keyMappings.javaClass.getMethod("getBookmark").invoke(keyMappings) as? IJeiKeyMapping
        }.getOrNull()
    }

    // Use JEI's own public matching (IJeiKeyMapping.isActiveAndMatches) rather than digging
    // out the wrapped vanilla KeyMapping — this mirrors how JEI itself dispatches key input
    // (see mezz.jei.gui.input.UserInput) and handles multi-key / modifier bindings correctly.
    private fun matchesKey(mapping: IJeiKeyMapping, keyCode: Int, scanCode: Int): Boolean {
        return runCatching {
            mapping.isActiveAndMatches(InputConstants.getKey(keyCode, scanCode))
        }.getOrDefault(false)
    }

    private fun toggleBookmark(bookmarkOverlay: Any, ingredientManager: IIngredientManager, stack: ItemStack): Boolean {
        return runCatching {
            // Use the explicit ItemStack type so we don't rely on JEI's single-arg type
            // inference. Returns empty if JEI considers the stack invalid/unregistered —
            // which is the most likely reason a card item silently fails to bookmark.
            val typedIngredient = ingredientManager
                .createTypedIngredient(VanillaTypes.ITEM_STACK, stack)
                .orElse(null)
            if (typedIngredient == null) {
                log.warn("JEI rejected card item for bookmarking (invalid/unregistered ingredient): {}", stack)
                return false
            }

            // runtime.bookmarkOverlay is IBookmarkOverlay; the real impl is BookmarkOverlay
            // (holds the bookmarkList). Search the class hierarchy in case it's wrapped.
            val bookmarkList = findFieldValue(bookmarkOverlay, "bookmarkList")
            if (bookmarkList == null) {
                log.warn("Could not access JEI bookmarkList on overlay {}", bookmarkOverlay.javaClass.name)
                return false
            }

            // JEI 19.27 replaced the static IngredientBookmark.create(typed, manager) with
            // an instance method on BookmarkFactory, which BookmarkList holds as a field.
            val bookmarkFactory = findFieldValue(bookmarkList, "bookmarkFactory")
            if (bookmarkFactory == null) {
                log.warn("Could not access JEI bookmarkFactory on {}", bookmarkList.javaClass.name)
                return false
            }

            val ingredientBookmark = bookmarkFactory.javaClass
                .getMethod("create", ITypedIngredient::class.java)
                .invoke(bookmarkFactory, typedIngredient)
                ?: return false

            val bookmarkInterface = Class.forName("mezz.jei.gui.bookmarks.IBookmark")
            bookmarkList.javaClass
                .getMethod("toggleBookmark", bookmarkInterface)
                .invoke(bookmarkList, ingredientBookmark)

            true
        }.getOrElse { error ->
            if (!bookmarkReflectionFailed) {
                bookmarkReflectionFailed = true
                log.error("Failed to bridge ExBingo card hover with JEI bookmarks", error)
            }
            false
        }
    }

    private fun findFieldValue(target: Any, name: String): Any? {
        var cls: Class<*>? = target.javaClass
        while (cls != null) {
            val field = cls.declaredFields.firstOrNull { it.name == name }
            if (field != null) {
                return field.apply { isAccessible = true }.get(target)
            }
            cls = cls.superclass
        }
        return null
    }
}
