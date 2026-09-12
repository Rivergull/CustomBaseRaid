package com.hamsun5.custombaseraid.client.gui;

import net.minecraft.world.level.Level;

public class DummyWorld {
    public static Level getInstance() {
        return DummyClientWorld.getLevel();
    }
}
