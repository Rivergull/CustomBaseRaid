package com.hamsun5.custombaseraid.client.gui;

import com.hamsun5.custombaseraid.config.ModConfig;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ClothMobEntry extends AbstractConfigListEntry<Integer> {

    private final ModConfig.MobEntry mobEntry;
    private final EditBox countBox;
    private final Button resetButton;
    private final Button deleteButton;
    private final List<GuiEventListener> children = new ArrayList<>();
    private final List<NarratableEntry> narratables = new ArrayList<>();
    private final int defaultCount;

    public ClothMobEntry(ModConfig.MobEntry mobEntry, Runnable onDelete) {
        super(Component.literal(mobEntry.mobId), false);
        this.mobEntry = mobEntry;
        this.defaultCount = Math.max(1, mobEntry.count);

        Minecraft mc = Minecraft.getInstance();

        // 1. Count edit box
        this.countBox = new EditBox(mc.font, 0, 0, 42, 18, Component.literal("Count"));
        this.countBox.setValue(String.valueOf(mobEntry.count));
        this.countBox.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        this.countBox.setResponder(val -> {
            try {
                if (!val.isEmpty()) {
                    int c = Integer.parseInt(val);
                    mobEntry.count = Math.max(1, c);
                }
            } catch (NumberFormatException ignored) {
            }
        });

        // 2. Reset button
        this.resetButton = Button.builder(Component.literal("Reset"), b -> {
            mobEntry.count = defaultCount;
            countBox.setValue(String.valueOf(defaultCount));
        }).bounds(0, 0, 44, 18).build();

        // 3. Delete button
        this.deleteButton = Button.builder(Component.literal("\u00a7c🗑"), b -> onDelete.run())
                .bounds(0, 0, 24, 18)
                .build();

        this.children.add(this.countBox);
        this.children.add(this.resetButton);
        this.children.add(this.deleteButton);

        this.narratables.add(this.countBox);
        this.narratables.add(this.resetButton);
        this.narratables.add(this.deleteButton);
    }

    @Override
    public Integer getValue() {
        try {
            return Integer.parseInt(countBox.getValue());
        } catch (Exception e) {
            return mobEntry.count;
        }
    }

    @Override
    public Optional<Integer> getDefaultValue() {
        return Optional.of(defaultCount);
    }

    @Override
    public void save() {
        try {
            int c = Integer.parseInt(countBox.getValue());
            mobEntry.count = Math.max(1, c);
        } catch (Exception ignored) {
        }
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return children;
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
        return narratables;
    }

    @Override
    public void render(GuiGraphics graphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean isHovered, float delta) {
        super.render(graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, delta);

        int textY = y + (entryHeight - 8) / 2;
        String mobLabel = "\u00a7e👾 " + mobEntry.mobId;
        graphics.drawString(Minecraft.getInstance().font, mobLabel, x + 4, textY, 0xFFFFFF, false);

        int buttonY = y + (entryHeight - 18) / 2;

        // Position Delete button at right edge
        int delX = x + entryWidth - 28;
        deleteButton.setX(delX);
        deleteButton.setY(buttonY);
        deleteButton.render(graphics, mouseX, mouseY, delta);

        // Position Reset button to left of Delete
        int resetX = delX - 48;
        resetButton.setX(resetX);
        resetButton.setY(buttonY);
        resetButton.render(graphics, mouseX, mouseY, delta);

        // Position Count box to left of Reset
        int countX = resetX - 46;
        countBox.setX(countX);
        countBox.setY(buttonY);
        countBox.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public int getItemHeight() {
        return 24;
    }
}
