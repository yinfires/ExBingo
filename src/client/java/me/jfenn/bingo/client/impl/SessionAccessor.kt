package me.jfenn.bingo.client.impl

import me.jfenn.bingo.client.platform.ISessionAccessor
import net.minecraft.client.Minecraft
import java.util.*

object SessionAccessor : ISessionAccessor {
    override fun getPlayerUuid(): UUID? {
        return Minecraft.getInstance().user.profileId
    }
}
