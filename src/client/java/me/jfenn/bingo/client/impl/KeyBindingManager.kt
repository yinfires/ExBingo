package me.jfenn.bingo.client.impl

import com.mojang.blaze3d.platform.InputConstants
import me.jfenn.bingo.client.platform.IKeyBinding
import me.jfenn.bingo.client.platform.IKeyBindingManager
import net.minecraft.client.KeyMapping
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent

class KeyBindingManager : IKeyBindingManager {
    companion object {
        private val mappings = mutableListOf<KeyMapping>()

        @JvmStatic
        fun registerMappings(event: RegisterKeyMappingsEvent) {
            mappings.forEach(event::register)
        }
    }

    override fun registerKey(translationKey: String, code: Int, category: String): IKeyBinding {
        val mapping = KeyMapping(
            translationKey,
            InputConstants.Type.KEYSYM,
            code,
            category,
        )
        mappings += mapping
        return KeyBindingImpl(mapping)
    }

    class KeyBindingImpl(
        private val binding: KeyMapping,
    ) : IKeyBinding {
        override fun isPressed(): Boolean = binding.isDown
        override fun wasPressed(): Boolean = binding.consumeClick()
    }
}
