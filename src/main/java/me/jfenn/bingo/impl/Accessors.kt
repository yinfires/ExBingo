package me.jfenn.bingo.impl

import me.jfenn.bingo.mixin.*
import net.minecraft.world.food.FoodData
import net.minecraft.world.level.saveddata.maps.MapItemSavedData
import net.minecraft.server.MinecraftServer
import net.minecraft.util.thread.BlockableEventLoop
import net.minecraft.world.entity.raid.Raids

val FoodData.accessor get() = this as HungerManagerAccessor
val Raids.raidManagerAccessor get() = this as RaidManagerAccessor
val MapItemSavedData.accessor get() = this as MapStateAccessor
val MinecraftServer.accessor get() = this as MinecraftServerAccessor
val BlockableEventLoop<*>.threadExecutorAccessor get() = this as ThreadExecutorAccessor
