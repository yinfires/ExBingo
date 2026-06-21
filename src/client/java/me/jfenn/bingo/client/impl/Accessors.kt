package me.jfenn.bingo.client.impl

import me.jfenn.bingo.client.mixin.CreateWorldScreenAccessor
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen

val CreateWorldScreen.accessor get() = this as CreateWorldScreenAccessor
