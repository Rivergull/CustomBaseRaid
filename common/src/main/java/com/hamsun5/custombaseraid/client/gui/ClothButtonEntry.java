package com.hamsun5.custombaseraid.client.gui;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ClothButtonEntry extends AbstractConfigListEntry<Void> {

    private final Button button;
    private final List<Button> children;

    public ClothButtonEntry(Component label, Component buttonText, Runnable onClick) {
        super(label, false);
        this.button = Button.builder(buttonText, b -> onClick.run())
                .bounds(0, 0, 160, 20)
                .build();
        this.children = Collections.singletonList(this.button);
    }

    public ClothButtonEntry(Component label, Component buttonText, int buttonWidth, Runnable onClick) {
        super(label, false);
        this.button = Button.builder(buttonText, b -> onClick.run())
                .bounds(0, 0, buttonWidth, 20)
                .build();
        this.children = Collections.singletonList(this.button);
    }

    @Override
    public Void getValue() {
        return null;
    }

    @Override
    public Optional<Void> getDefaultValue() {
        return Optional.empty();
    }

    @Override
    public void save() {
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return children;
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
        return children;
    }

    @Override
    public void render(GuiGraphics graphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean isHovered, float delta) {
        super.render(graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, delta);

        int textY = y + (entryHeight - 8) / 2;
        graphics.drawString(Minecraft.getInstance().font, getFieldName(), x, textY, 0xFFFFFF, false);

        int buttonX = x + entryWidth - button.getWidth() - 4;
        int buttonY = y + (entryHeight - button.getHeight()) / 2;
        button.setX(buttonX);
        button.setY(buttonY);
        button.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public int getItemHeight() {
        return 24;
    }
}
