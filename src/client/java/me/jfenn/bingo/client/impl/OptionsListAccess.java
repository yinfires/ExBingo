package me.jfenn.bingo.client.impl;

import net.minecraft.client.gui.components.OptionsList;

final class OptionsListAccess {
    private OptionsListAccess() {
    }

    static void clear(OptionsList list) {
        list.children().clear();
    }
}
