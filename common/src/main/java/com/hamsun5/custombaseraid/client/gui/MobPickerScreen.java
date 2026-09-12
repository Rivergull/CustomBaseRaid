package com.hamsun5.custombaseraid.client.gui;

import com.hamsun5.custombaseraid.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class MobPickerScreen extends Screen {

    private final Screen parentScreen;
    private final Consumer<ModConfig.MobEntry> onSelect;
    private final List<MobInfo> allMobs = new ArrayList<>();
    private final List<MobInfo> filteredMobs = new ArrayList<>();
    private final Map<EntityType<?>, LivingEntity> dummyEntityCache = new HashMap<>();

    private EditBox searchBox;
    private EditBox countBox;
    private MobList mobList;
    private MobInfo selectedMob;
    private Button confirmButton;
    private int mobCount = 3;

    public static class MobInfo {
        public final ResourceLocation id;
        public final String name;
        public final EntityType<?> type;
        public final ItemStack icon;
        public LivingEntity cachedEntity;

        public MobInfo(ResourceLocation id, String name, EntityType<?> type, ItemStack icon, LivingEntity cachedEntity) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.icon = icon;
            this.cachedEntity = cachedEntity;
        }
    }

    public MobPickerScreen(Screen parentScreen, Consumer<ModConfig.MobEntry> onSelect) {
        super(Component.literal("Select Raid Mob"));
        this.parentScreen = parentScreen;
        this.onSelect = onSelect;
        loadHostileMobs();
    }

    private void loadHostileMobs() {
        allMobs.clear();
        Level level = DummyWorld.getInstance();

        for (var entry : BuiltInRegistries.ENTITY_TYPE.entrySet()) {
            EntityType<?> type = entry.getValue();
            ResourceLocation id = entry.getKey().location();

            // 1. Must be summonable
            if (!type.canSummon()) continue;

            // 2. Must belong to MONSTER category (eliminates ArmorStand, Arrow, Animals, Villagers, etc.)
            if (type.getCategory() != MobCategory.MONSTER) continue;

            // 3. Exclude known neutral / "attack if provoked" mobs
            String path = id.getPath();
            if (path.contains("zombified_piglin") || path.equals("enderman") || path.equals("piglin")) {
                continue;
            }

            // 4. Try instantiating dummy entity to verify Enemy / Monster status and exclude NeutralMob
            LivingEntity livingEntity = null;
            if (level != null) {
                try {
                    Entity entity = type.create(level);
                    if (entity instanceof LivingEntity le) {
                        // Exclude neutral mobs
                        if (entity instanceof NeutralMob) {
                            continue;
                        }
                        // Must be a hostile enemy or monster
                        if (!(entity instanceof Enemy) && !(entity instanceof Monster)) {
                            continue;
                        }
                        livingEntity = le;
                        dummyEntityCache.put(type, le);
                    } else {
                        continue; // Not a living entity
                    }
                } catch (Throwable ignored) {
                }
            }

            // Spawn egg icon fallback for list item
            ItemStack icon;
            try {
                SpawnEggItem egg = SpawnEggItem.byId(type);
                icon = (egg != null) ? new ItemStack(egg) : new ItemStack(Items.SPAWNER);
            } catch (Throwable t) {
                icon = new ItemStack(Items.SPAWNER);
            }

            String name = type.getDescription().getString();
            allMobs.add(new MobInfo(id, name, type, icon, livingEntity));
        }

        allMobs.sort(Comparator.comparing(a -> a.name.toLowerCase()));
        filteredMobs.addAll(allMobs);

        if (!filteredMobs.isEmpty()) {
            selectedMob = filteredMobs.get(0);
        }
    }

    @Override
    protected void init() {
        super.init();

        int listWidth = Math.max(200, (int) (this.width * 0.52));
        int searchY = 28;

        // Search Box
        this.searchBox = new EditBox(this.font, 20, searchY, listWidth - 20, 20, Component.literal("Search Mobs"));
        this.searchBox.setHint(Component.literal("Search hostile mob name or ID..."));
        this.searchBox.setResponder(this::filterMobs);
        this.addRenderableWidget(this.searchBox);

        // Mob List (Left Side)
        this.mobList = new MobList(this.minecraft, listWidth, this.height - 90, 54, 30);
        this.mobList.updateEntries(filteredMobs);
        this.addRenderableWidget(this.mobList);

        // Right Side Controls (Inspector Pane)
        int rightPaneX = listWidth + 15;
        int rightPaneWidth = this.width - rightPaneX - 20;
        int bottomY = this.height - 36;

        // Count Stepper Buttons
        int countControlsX = rightPaneX + 10;
        this.addRenderableWidget(Button.builder(Component.literal("-"), btn -> {
            mobCount = Math.max(1, mobCount - 1);
            if (countBox != null) countBox.setValue(String.valueOf(mobCount));
        }).bounds(countControlsX, bottomY - 30, 20, 20).build());

        this.countBox = new EditBox(this.font, countControlsX + 24, bottomY - 29, 42, 18, Component.literal("Count"));
        this.countBox.setValue(String.valueOf(mobCount));
        this.countBox.setResponder(val -> {
            try {
                mobCount = Math.max(1, Integer.parseInt(val.trim()));
            } catch (NumberFormatException ignored) {
            }
        });
        this.addRenderableWidget(this.countBox);

        this.addRenderableWidget(Button.builder(Component.literal("+"), btn -> {
            mobCount = Math.min(200, mobCount + 1);
            if (countBox != null) countBox.setValue(String.valueOf(mobCount));
        }).bounds(countControlsX + 70, bottomY - 30, 20, 20).build());

        // Confirm "Add Mob to Wave" Button
        int btnWidth = Math.max(120, rightPaneWidth - 100);
        this.confirmButton = Button.builder(Component.literal("\u2694 Add Mob to Wave"), btn -> {
            if (selectedMob != null) {
                onSelect.accept(new ModConfig.MobEntry(selectedMob.id.toString(), mobCount));
            }
        }).bounds(countControlsX + 96, bottomY - 30, btnWidth, 20).build();
        this.confirmButton.active = (selectedMob != null);
        this.addRenderableWidget(this.confirmButton);

        // Cancel Button (Bottom Right)
        this.addRenderableWidget(Button.builder(Component.literal("Back"), btn -> {
            Minecraft.getInstance().setScreen(parentScreen);
        }).bounds(this.width - 80, bottomY, 60, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        // Regular 20 TPS entity animation ticking (prevents ultra fast spinning!)
        if (selectedMob != null) {
            LivingEntity dummy = getOrCreateDummy(selectedMob);
            if (dummy != null) {
                dummy.tickCount++;
            }
        }
    }

    private void filterMobs(String query) {
        filteredMobs.clear();
        String q = query.toLowerCase().trim();
        for (MobInfo info : allMobs) {
            if (info.name.toLowerCase().contains(q) || info.id.toString().toLowerCase().contains(q)) {
                filteredMobs.add(info);
            }
        }
        if (mobList != null) {
            mobList.updateEntries(filteredMobs);
        }
        if (!filteredMobs.contains(selectedMob)) {
            selectedMob = filteredMobs.isEmpty() ? null : filteredMobs.get(0);
            if (confirmButton != null) confirmButton.active = (selectedMob != null);
        }
    }

    private LivingEntity getOrCreateDummy(MobInfo info) {
        if (info == null) return null;
        if (info.cachedEntity != null) return info.cachedEntity;

        Level level = DummyWorld.getInstance();
        if (level != null) {
            try {
                Entity e = info.type.create(level);
                if (e instanceof LivingEntity le) {
                    info.cachedEntity = le;
                    dummyEntityCache.put(info.type, le);
                    return le;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Render solid dirt menu background so the world is fully obscured
        Screen.renderMenuBackgroundTexture(graphics, Screen.MENU_BACKGROUND, 0, 0, 0.0F, 0.0F, this.width, this.height);
        // Semi-dark tint overlay
        graphics.fill(0, 0, this.width, this.height, 0x80000000);

        super.render(graphics, mouseX, mouseY, partialTick);

        // Header Title
        graphics.drawCenteredString(this.font, "\u00a7l\u00a7eSelect Hostile Raid Mob", this.width / 2, 10, 0xFFFFFF);

        // Right Side: 3D Mob Preview Box
        int listWidth = Math.max(200, (int) (this.width * 0.52));
        int rightPaneX = listWidth + 10;
        int rightPaneWidth = this.width - rightPaneX - 15;
        int previewTop = 28;
        int previewBottom = this.height - 70;

        // Dark Container Panel for 3D Mob Preview
        graphics.fill(rightPaneX, previewTop, rightPaneX + rightPaneWidth, previewBottom, 0xAA000000);
        graphics.renderOutline(rightPaneX, previewTop, rightPaneWidth, previewBottom - previewTop, 0xFF555555);

        if (selectedMob != null) {
            // Selected Mob Name
            graphics.drawCenteredString(this.font, "\u00a7e\u00a7l" + selectedMob.name, rightPaneX + rightPaneWidth / 2, previewTop + 8, 0xFFFFFF);
            graphics.drawCenteredString(this.font, "\u00a77" + selectedMob.id.toString(), rightPaneX + rightPaneWidth / 2, previewTop + 20, 0xAAAAAA);

            // 3D Render Entity with larger scale
            LivingEntity dummy = getOrCreateDummy(selectedMob);
            if (dummy != null) {
                float bbWidth = dummy.getBbWidth();
                float bbHeight = dummy.getBbHeight();
                float bbMax = Math.max(0.5F, Math.max(bbWidth, bbHeight));
                // Increased scale for prominent 3D preview
                int scale = (int) Math.min(80, 52.0F / bbMax);

                int entityCenterX = rightPaneX + rightPaneWidth / 2;
                int entityCenterY = previewTop + (previewBottom - previewTop) / 2 + 30;

                try {
                    InventoryScreen.renderEntityInInventoryFollowsMouse(
                            graphics,
                            entityCenterX - 55, entityCenterY - 80,
                            entityCenterX + 55, entityCenterY + 30,
                            scale,
                            0.0625F,
                            (float) mouseX, (float) mouseY,
                            dummy
                    );
                } catch (Throwable ignored) {
                    // Fallback to item icon if entity model rendering has issue
                    graphics.renderItem(selectedMob.icon, entityCenterX - 8, entityCenterY - 20);
                }
            } else {
                // Fallback icon
                int iconX = rightPaneX + rightPaneWidth / 2 - 16;
                int iconY = previewTop + (previewBottom - previewTop) / 2 - 16;
                graphics.renderItem(selectedMob.icon, iconX, iconY);
            }

            // Stats / Type Label
            graphics.drawCenteredString(this.font, "\u00a7aHostile Monster", rightPaneX + rightPaneWidth / 2, previewBottom - 16, 0x55FF55);
        } else {
            graphics.drawCenteredString(this.font, "\u00a77No mob selected", rightPaneX + rightPaneWidth / 2, previewTop + 40, 0x888888);
        }

        // Count Label
        graphics.drawString(this.font, "\u00a77Spawn Count:", rightPaneX + 10, this.height - 42, 0xCCCCCC);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parentScreen);
    }

    public class MobList extends ObjectSelectionList<MobList.MobEntry> {

        public MobList(Minecraft minecraft, int width, int height, int y0, int itemHeight) {
            super(minecraft, width, height, y0, itemHeight);
        }

        public void updateEntries(List<MobInfo> list) {
            this.clearEntries();
            for (MobInfo info : list) {
                this.addEntry(new MobEntry(info));
            }
            this.setScrollAmount(0);
        }

        public class MobEntry extends ObjectSelectionList.Entry<MobEntry> {
            private final MobInfo info;

            public MobEntry(MobInfo info) {
                this.info = info;
            }

            @Override
            public Component getNarration() {
                return Component.literal(info.name);
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
                boolean isSelected = selectedMob != null && selectedMob.id.equals(info.id);

                if (isSelected || hovering) {
                    int bg = isSelected ? 0x55FFFF55 : 0x22FFFFFF;
                    graphics.fill(left, top, left + width, top + height, bg);
                }

                // Render Item Icon / Spawn Egg
                graphics.renderItem(info.icon, left + 4, top + 5);

                int nameColor = isSelected ? 0xFFFF55 : (hovering ? 0xFFFFFF : 0xDDDDDD);
                graphics.drawString(font, info.name, left + 26, top + 5, nameColor);
                graphics.drawString(font, "\u00a78" + info.id.toString(), left + 26, top + 16, 0x777777);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button == 0) {
                    selectedMob = info;
                    if (confirmButton != null) {
                        confirmButton.active = true;
                    }
                    return true;
                }
                return false;
            }
        }
    }
}
