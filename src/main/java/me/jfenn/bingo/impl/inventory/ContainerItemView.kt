package me.jfenn.bingo.impl.inventory

import me.jfenn.bingo.platform.inventory.IContainerItemView
import me.jfenn.bingo.platform.item.IItemStack
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.component.BundleContents
import net.minecraft.world.item.component.ItemContainerContents
import net.minecraft.world.item.ItemStack

interface ContainerItemView {
    class Bundle(
        val bundleItem: ItemStack,
        override val stack: IItemStack
    ) : IContainerItemView {
        override fun replace(newStack: IItemStack) {
            val bundleComponent = bundleItem[DataComponents.BUNDLE_CONTENTS]

            val newBundleComponent = BundleContents(
                bundleComponent?.items()
                    ?.mapNotNull { bundleStack ->
                        if (bundleStack === stack.stack)
                            newStack.stack.takeUnless { it.isEmpty }
                        else bundleStack
                    }
                    ?.toList()
                    ?: emptyList()
            )

            bundleItem[DataComponents.BUNDLE_CONTENTS] = newBundleComponent
        }
    }

    class Container(
        val containerItem: ItemStack,
        override val stack: IItemStack
    ) : IContainerItemView {
        override fun replace(newStack: IItemStack) {
            val containerComponent = containerItem[DataComponents.CONTAINER]

            val newContainerComponent = ItemContainerContents.fromItems(
                containerComponent?.nonEmptyItems()
                    ?.mapNotNull { bundleStack ->
                        if (bundleStack === stack.stack)
                            newStack.stack.takeUnless { it.isEmpty }
                        else bundleStack
                    }
                    ?.toList()
                    ?: emptyList()
            )

            containerItem[DataComponents.CONTAINER] = newContainerComponent
        }
    }

    class Inventory(
        override val stack: IItemStack
    ) : IContainerItemView {
        override fun replace(newStack: IItemStack) {}
    }
}
