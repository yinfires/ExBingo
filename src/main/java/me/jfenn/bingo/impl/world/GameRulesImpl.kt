package me.jfenn.bingo.impl.world

import me.jfenn.bingo.platform.world.IGameRule
import me.jfenn.bingo.platform.world.IGameRules
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.GameRules

class GameRulesImpl(
    private val server: MinecraftServer,
) : IGameRules {

    override val announceAdvancements = BooleanRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS)
    override val showDeathMessages = BooleanRule(GameRules.RULE_SHOWDEATHMESSAGES)
    override val keepInventory = BooleanRule(GameRules.RULE_KEEPINVENTORY)
    override val pvp: IGameRule<Boolean> = object : IGameRule<Boolean> {
        override val name: String
            get() = throw NotImplementedError()
        override var value: Boolean
            get() = server.isPvpAllowed
            set(value) {
                server.setPvpAllowed(value)
            }
    }

    private inner class Visitor(val name: String) : GameRules.GameRuleTypeVisitor {
        var ret: IGameRule<*>? = null

        override fun visitBoolean(
            key: GameRules.Key<GameRules.BooleanValue>?,
            type: GameRules.Type<GameRules.BooleanValue>?
        ) {
            if (key?.id == name) {
                ret = BooleanRule(key)
            }
        }

        override fun visitInteger(
            key: GameRules.Key<GameRules.IntegerValue>?,
            type: GameRules.Type<GameRules.IntegerValue>?
        ) {
            if (key?.id == name) {
                ret = IntRule(key)
            }
        }
    }

    override fun get(name: String): IGameRule<*>? {
        val visitor = Visitor(name)
        GameRules.visitGameRuleTypes(visitor)
        return visitor.ret
    }

    inner class BooleanRule(
        private val key: GameRules.Key<GameRules.BooleanValue>,
    ) : IGameRule<Boolean> {
        override val name: String get() = key.id
        override var value: Boolean
            get() = server.gameRules.getRule(key).get()
            set(value) {
                server.gameRules.getRule(key).set(value, server)
            }
    }

    inner class IntRule(
        private val key: GameRules.Key<GameRules.IntegerValue>,
    ) : IGameRule<Int> {
        override val name: String get() = key.id
        override var value: Int
            get() = server.gameRules.getRule(key).get()
            set(value) {
                server.gameRules.getRule(key).set(value, server)
            }
    }
}
