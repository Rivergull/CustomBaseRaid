package com.hamsun5.custombaseraid.client.gui;

import net.minecraft.client.gui.screens.Screen;

public class RaidConfigScreenBuilder {
    public static Screen createScreen(Screen parent) {
        return ClothConfigScreenBuilder.build(parent);
    }
}
