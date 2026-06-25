package me.jfenn.bingo.mixinhandler

/**
 * Controls whether the vanilla advancement tree (the "L" screen) shows every
 * advancement immediately, instead of hiding entries until their prerequisites
 * are unlocked.
 *
 * When [enabled], [me.jfenn.bingo.mixin.PlayerAdvancementsRevealAllMixin] swaps
 * the "is done" visibility predicate for an always-true one, so every
 * advancement that has display info is sent to the client as visible. This is
 * read once from [me.jfenn.bingo.common.config.BingoConfig.revealAllAdvancements]
 * at server init.
 */
object RevealAllAdvancementsHelper {
    @JvmField
    var enabled: Boolean = false
}
