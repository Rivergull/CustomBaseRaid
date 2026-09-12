package com.hamsun5.custombaseraid.client.gui;

import com.hamsun5.custombaseraid.config.ConfigManager;
import com.hamsun5.custombaseraid.config.ModConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ClothConfigScreenBuilder {

    public static Screen build(Screen parent) {
        return buildMainScreen(parent, "General");
    }

    public static Screen build(Screen parent, String initialCategoryName) {
        return buildMainScreen(parent, initialCategoryName);
    }

    private static ConfigBuilder createBaseBuilder(Screen parent, Component title) {
        return ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(title)
                .setSavingRunnable(ConfigManager::save);
    }

    // =========================================================================
    // MAIN SCREEN (Holds [General] [Raid 1] [Raid 2] ... [+ Add Raid] tabs)
    // =========================================================================
    public static Screen buildMainScreen(Screen rootParent, String initialCategoryName) {
        ModConfig config = ConfigManager.getConfig();
        if (config.scheduledRaids == null) {
            config.scheduledRaids = new ArrayList<>();
        }

        ConfigBuilder builder = createBaseBuilder(rootParent, Component.literal("Custom Base Raid Settings"));
        ConfigEntryBuilder eb = builder.entryBuilder();

        // -------------------------------------------------------------
        // TAB 1: General Settings
        // -------------------------------------------------------------
        ConfigCategory generalCategory = builder.getOrCreateCategory(Component.literal("General"));

        generalCategory.addEntry(eb.startBooleanToggle(Component.literal("Enable Raids"), config.enableRaids)
                .setDefaultValue(true)
                .setSaveConsumer(val -> config.enableRaids = val)
                .build());

        List<String> modeOptions = Arrays.asList(
                "Scheduled Days (Fixed Trigger Day)",
                "Periodic Interval (Every X Days)",
                "Random Daily Chance (% Chance)"
        );
        String currentModeDesc = switch (config.raidScheduleMode != null ? config.raidScheduleMode.toLowerCase() : "scheduled") {
            case "periodic" -> "Periodic Interval (Every X Days)";
            case "random" -> "Random Daily Chance (% Chance)";
            default -> "Scheduled Days (Fixed Trigger Day)";
        };

        generalCategory.addEntry(eb.startSelector(Component.literal("Raid Trigger Mode"), modeOptions.toArray(new String[0]), currentModeDesc)
                .setDefaultValue("Scheduled Days (Fixed Trigger Day)")
                .setSaveConsumer(selected -> {
                    if (selected.startsWith("Periodic")) {
                        config.raidScheduleMode = "periodic";
                    } else if (selected.startsWith("Random")) {
                        config.raidScheduleMode = "random";
                    } else {
                        config.raidScheduleMode = "scheduled";
                    }
                })
                .build());

        generalCategory.addEntry(eb.startIntSlider(Component.literal("Default Hunt Delay (Seconds)"), config.defaultHuntDelaySeconds, 5, 60)
                .setDefaultValue(15)
                .setSaveConsumer(val -> config.defaultHuntDelaySeconds = val)
                .build());

        generalCategory.addEntry(eb.startBooleanToggle(Component.literal("Sound Effects Enabled"), config.soundEffectsEnabled)
                .setDefaultValue(true)
                .setSaveConsumer(val -> config.soundEffectsEnabled = val)
                .build());

        generalCategory.addEntry(eb.startBooleanToggle(Component.literal("Display Progress Bar"), config.raidBossBar)
                .setDefaultValue(true)
                .setSaveConsumer(val -> config.raidBossBar = val)
                .build());

        generalCategory.addEntry(eb.startBooleanToggle(Component.literal("Fail Raid On Player Death (Default)"), config.failOnPlayerDeath)
                .setDefaultValue(true)
                .setSaveConsumer(val -> config.failOnPlayerDeath = val)
                .build());

        // Extra spacing at bottom so content never collides with bottom buttons
        generalCategory.addEntry(eb.startTextDescription(Component.literal(" ")).build());
        generalCategory.addEntry(eb.startTextDescription(Component.literal(" ")).build());

        // -------------------------------------------------------------
        // TABS: Direct Raid Tabs in Header
        // -------------------------------------------------------------
        ConfigCategory targetInitialCat = generalCategory;

        for (int i = 0; i < config.scheduledRaids.size(); i++) {
            final int raidIdx = i;
            ModConfig.RaidDefinition raid = config.scheduledRaids.get(i);
            String tabName = raid.name != null && !raid.name.trim().isEmpty() ? raid.name.trim() : ("Raid " + (i + 1));
            ConfigCategory raidCategory = builder.getOrCreateCategory(Component.literal(tabName));
            populateRaidTabEntries(raidCategory, eb, rootParent, raid, raidIdx, tabName, config);

            if (initialCategoryName != null && initialCategoryName.equals(tabName)) {
                targetInitialCat = raidCategory;
            }
        }

        // -------------------------------------------------------------
        // TAB: + Add Raid in Header
        // -------------------------------------------------------------
        ConfigCategory addCategory = builder.getOrCreateCategory(Component.literal("\u00a7a+ Add Raid"));
        addCategory.addEntry(new ClothButtonEntry(
                Component.literal("New Raid:"),
                Component.literal("\u00a7a\u00a7l\u2795 Confirm Add New Raid"),
                220,
                () -> {
                    ConfigManager.save();
                    ModConfig.RaidDefinition newRaid = createDefaultRaid(config.scheduledRaids.size() + 1);
                    config.scheduledRaids.add(newRaid);
                    ConfigManager.save();
                    Minecraft.getInstance().setScreen(buildMainScreen(rootParent, newRaid.name));
                }
        ));
        addCategory.addEntry(eb.startTextDescription(Component.literal(" ")).build());
        addCategory.addEntry(eb.startTextDescription(Component.literal(" ")).build());

        builder.setFallbackCategory(targetInitialCat);

        return builder.build();
    }

    private static void populateRaidTabEntries(ConfigCategory cat, ConfigEntryBuilder eb, Screen rootParent,
                                               ModConfig.RaidDefinition raid, int raidIndex, String tabName, ModConfig config) {
        // 1. Raid Name
        cat.addEntry(eb.startStrField(Component.literal("Raid Name"), raid.name != null ? raid.name : "Base Raid")
                .setDefaultValue("Base Raid")
                .setSaveConsumer(val -> raid.name = val)
                .build());

        // 2. Adaptive Trigger Field based on General Schedule Mode
        String mode = config.raidScheduleMode != null ? config.raidScheduleMode.toLowerCase() : "scheduled";
        String triggerLabel = switch (mode) {
            case "periodic" -> "Trigger Interval (Every X Days)";
            case "random" -> "Trigger Chance (%) (1 - 100)";
            default -> "Trigger Day (Specific In-Game Day)";
        };
        int maxLimit = mode.equals("random") ? 100 : 100000;

        cat.addEntry(eb.startIntField(Component.literal(triggerLabel), raid.triggerDay)
                .setMin(1)
                .setMax(maxLimit)
                .setDefaultValue(mode.equals("random") ? 25 : 3)
                .setSaveConsumer(val -> raid.triggerDay = Math.max(1, Math.min(val, maxLimit)))
                .build());

        cat.addEntry(eb.startTextDescription(
                Component.literal("\u00a76--- Raid Settings Categories ---")
        ).build());

        // 3. Sub-Category Navigation Buttons - all link back to the main tabbed screen!
        cat.addEntry(new ClothButtonEntry(
                Component.literal("Event Messages:"),
                Component.literal("\u00a7e\ud83d\udcac Event Messages >"),
                200,
                () -> {
                    ConfigManager.save();
                    Minecraft.getInstance().setScreen(buildEventMessagesScreen(buildMainScreen(rootParent, tabName), raidIndex, rootParent, tabName));
                }
        ));

        cat.addEntry(new ClothButtonEntry(
                Component.literal("Spawn & Timers:"),
                Component.literal("\u00a7e\u23f1 Spawn & Time >"),
                200,
                () -> {
                    ConfigManager.save();
                    Minecraft.getInstance().setScreen(buildSpawnTimeScreen(buildMainScreen(rootParent, tabName), raidIndex, rootParent, tabName));
                }
        ));

        cat.addEntry(new ClothButtonEntry(
                Component.literal("Victory, Defeat & Rewards:"),
                Component.literal("\u00a7e\ud83c\udfc6 Victory & Defeat >"),
                200,
                () -> {
                    ConfigManager.save();
                    Minecraft.getInstance().setScreen(buildVictoryDefeatScreen(buildMainScreen(rootParent, tabName), raidIndex, rootParent, tabName));
                }
        ));

        cat.addEntry(new ClothButtonEntry(
                Component.literal("Advancement Gate:"),
                Component.literal("\u00a7e\ud83c\udf96 Advancement Requirement >"),
                200,
                () -> {
                    ConfigManager.save();
                    Minecraft.getInstance().setScreen(buildAdvancementScreen(buildMainScreen(rootParent, tabName), raidIndex, rootParent, tabName));
                }
        ));

        int waveCount = raid.waves != null ? raid.waves.size() : 0;
        cat.addEntry(new ClothButtonEntry(
                Component.literal("Waves (" + waveCount + " configured):"),
                Component.literal("\u00a7b\u00a7l\u2694 Waves Configuration >"),
                200,
                () -> {
                    ConfigManager.save();
                    Minecraft.getInstance().setScreen(buildWavesListScreen(buildMainScreen(rootParent, tabName), raidIndex, rootParent, tabName));
                }
        ));

        cat.addEntry(eb.startTextDescription(
                Component.literal("\u00a7c--- Danger Zone ---")
        ).build());

        // 4. Delete Raid Button
        cat.addEntry(new ClothButtonEntry(
                Component.literal("Remove This Raid:"),
                Component.literal("\u00a7c\u00a7l\ud83d\uddd1 Delete Raid"),
                200,
                () -> {
                    ConfigManager.save();
                    if (raidIndex >= 0 && raidIndex < config.scheduledRaids.size()) {
                        config.scheduledRaids.remove(raidIndex);
                        ConfigManager.save();
                    }
                    Minecraft.getInstance().setScreen(buildMainScreen(rootParent, "General"));
                }
        ));

        // Bottom spacing padding
        cat.addEntry(eb.startTextDescription(Component.literal(" ")).build());
        cat.addEntry(eb.startTextDescription(Component.literal(" ")).build());
    }

    // =========================================================================
    // SUB-SCREEN: EVENT MESSAGES
    // =========================================================================
    public static Screen buildEventMessagesScreen(Screen parent, int raidIndex, Screen rootParent, String tabName) {
        ModConfig config = ConfigManager.getConfig();
        if (raidIndex < 0 || raidIndex >= config.scheduledRaids.size()) return parent;
        ModConfig.RaidDefinition raid = config.scheduledRaids.get(raidIndex);

        ConfigBuilder builder = createBaseBuilder(parent, Component.literal("Event Messages: " + raid.name));
        ConfigEntryBuilder eb = builder.entryBuilder();
        ConfigCategory cat = builder.getOrCreateCategory(Component.literal("Messages"));

        cat.addEntry(eb.startStrField(Component.literal("Warning Time"), raid.warningTime != null ? raid.warningTime : "dusk")
                .setDefaultValue("dusk")
                .setSaveConsumer(val -> raid.warningTime = val)
                .build());

        cat.addEntry(eb.startStrField(Component.literal("Warning Message"), raid.warningMessage != null ? raid.warningMessage : "")
                .setDefaultValue("A raid approaches your base! Prepare defenses!")
                .setSaveConsumer(val -> raid.warningMessage = val)
                .build());

        cat.addEntry(eb.startStrField(Component.literal("Victory Message"), raid.victoryMessage != null ? raid.victoryMessage : "")
                .setDefaultValue("Victory! All raid waves defeated!")
                .setSaveConsumer(val -> raid.victoryMessage = val)
                .build());

        cat.addEntry(eb.startStrField(Component.literal("Defeat Message"), raid.defeatMessage != null ? raid.defeatMessage : "")
                .setDefaultValue("Raid Failed! Base defenses collapsed.")
                .setSaveConsumer(val -> raid.defeatMessage = val)
                .build());

        cat.addEntry(eb.startTextDescription(Component.literal(" ")).build());
        cat.addEntry(eb.startTextDescription(Component.literal(" ")).build());

        return builder.build();
    }

    // =========================================================================
    // SUB-SCREEN: SPAWN & TIME
    // =========================================================================
    public static Screen buildSpawnTimeScreen(Screen parent, int raidIndex, Screen rootParent, String tabName) {
        ModConfig config = ConfigManager.getConfig();
        if (raidIndex < 0 || raidIndex >= config.scheduledRaids.size()) return parent;
        ModConfig.RaidDefinition raid = config.scheduledRaids.get(raidIndex);

        ConfigBuilder builder = createBaseBuilder(parent, Component.literal("Spawn & Time: " + raid.name));
        ConfigEntryBuilder eb = builder.entryBuilder();
        ConfigCategory cat = builder.getOrCreateCategory(Component.literal("Spawn & Time"));

        cat.addEntry(eb.startIntSlider(Component.literal("Spawn Radius (Blocks)"), raid.spawnRadius, 16, 64)
                .setDefaultValue(32)
                .setSaveConsumer(val -> raid.spawnRadius = val)
                .build());

        cat.addEntry(eb.startIntSlider(Component.literal("Hunt Delay (Seconds)"), raid.huntDelaySeconds, 5, 60)
                .setDefaultValue(15)
                .setSaveConsumer(val -> raid.huntDelaySeconds = val)
                .build());

        cat.addEntry(eb.startBooleanToggle(Component.literal("Enable Time Limit"), raid.enableTimeLimit)
                .setDefaultValue(true)
                .setSaveConsumer(val -> raid.enableTimeLimit = val)
                .build());

        cat.addEntry(eb.startIntSlider(Component.literal("Time Limit (Seconds)"), raid.timeLimitSeconds, 30, 600)
                .setDefaultValue(180)
                .setSaveConsumer(val -> raid.timeLimitSeconds = val)
                .build());

        cat.addEntry(eb.startTextDescription(Component.literal(" ")).build());
        cat.addEntry(eb.startTextDescription(Component.literal(" ")).build());

        return builder.build();
    }

    // =========================================================================
    // SUB-SCREEN: VICTORY & DEFEAT
    // =========================================================================
    public static Screen buildVictoryDefeatScreen(Screen parent, int raidIndex, Screen rootParent, String tabName) {
        ModConfig config = ConfigManager.getConfig();
        if (raidIndex < 0 || raidIndex >= config.scheduledRaids.size()) return parent;
        ModConfig.RaidDefinition raid = config.scheduledRaids.get(raidIndex);

        ConfigBuilder builder = createBaseBuilder(parent, Component.literal("Victory & Defeat: " + raid.name));
        ConfigEntryBuilder eb = builder.entryBuilder();
        ConfigCategory cat = builder.getOrCreateCategory(Component.literal("Victory & Defeat"));

        cat.addEntry(eb.startBooleanToggle(Component.literal("Fail On Player Death"), raid.failOnPlayerDeath)
                .setDefaultValue(true)
                .setSaveConsumer(val -> raid.failOnPlayerDeath = val)
                .build());

        cat.addEntry(eb.startBooleanToggle(Component.literal("Highlight Remaining Mobs"), raid.highlightRemainingMobs)
                .setDefaultValue(true)
                .setSaveConsumer(val -> raid.highlightRemainingMobs = val)
                .build());

        cat.addEntry(eb.startIntSlider(Component.literal("Highlight Threshold (Mobs Left)"), raid.highlightThreshold, 1, 20)
                .setDefaultValue(3)
                .setSaveConsumer(val -> raid.highlightThreshold = Math.max(1, Math.min(val, 20)))
                .build());

        cat.addEntry(eb.startBooleanToggle(Component.literal("Enable Rewards"), raid.enableRewards)
                .setDefaultValue(true)
                .setSaveConsumer(val -> raid.enableRewards = val)
                .build());

        if (raid.rewards == null) {
            raid.rewards = new ModConfig.RewardSettings();
        }

        cat.addEntry(eb.startIntField(Component.literal("Experience Points Reward"), raid.rewards.experiencePoints)
                .setDefaultValue(500)
                .setSaveConsumer(val -> raid.rewards.experiencePoints = val)
                .build());

        cat.addEntry(eb.startStrField(Component.literal("Reward Command"), raid.rewards.runCommand != null ? raid.rewards.runCommand : "")
                .setDefaultValue("")
                .setSaveConsumer(val -> raid.rewards.runCommand = val)
                .build());

        cat.addEntry(eb.startTextDescription(Component.literal(" ")).build());
        cat.addEntry(eb.startTextDescription(Component.literal(" ")).build());

        return builder.build();
    }

    // =========================================================================
    // SUB-SCREEN: ADVANCEMENT
    // =========================================================================
    public static Screen buildAdvancementScreen(Screen parent, int raidIndex, Screen rootParent, String tabName) {
        ModConfig config = ConfigManager.getConfig();
        if (raidIndex < 0 || raidIndex >= config.scheduledRaids.size()) return parent;
        ModConfig.RaidDefinition raid = config.scheduledRaids.get(raidIndex);

        ConfigBuilder builder = createBaseBuilder(parent, Component.literal("Advancement Requirement: " + raid.name));
        ConfigEntryBuilder eb = builder.entryBuilder();
        ConfigCategory cat = builder.getOrCreateCategory(Component.literal("Advancement"));

        cat.addEntry(eb.startBooleanToggle(Component.literal("Require Advancement To Trigger"), raid.requireAdvancement)
                .setDefaultValue(false)
                .setSaveConsumer(val -> raid.requireAdvancement = val)
                .build());

        cat.addEntry(eb.startStrField(Component.literal("Required Advancement ID"),
                raid.requiredAdvancementId != null ? raid.requiredAdvancementId : "minecraft:story/mine_diamond")
                .setDefaultValue("minecraft:story/mine_diamond")
                .setSaveConsumer(val -> raid.requiredAdvancementId = val)
                .build());

        cat.addEntry(eb.startTextDescription(Component.literal(" ")).build());
        cat.addEntry(eb.startTextDescription(Component.literal(" ")).build());

        return builder.build();
    }

    // =========================================================================
    // SUB-SCREEN: WAVES LIST (Wave 1, Wave 2, Add Wave)
    // =========================================================================
    public static Screen buildWavesListScreen(Screen parent, int raidIndex, Screen rootParent, String tabName) {
        ModConfig config = ConfigManager.getConfig();
        if (raidIndex < 0 || raidIndex >= config.scheduledRaids.size()) {
            return parent;
        }

        ModConfig.RaidDefinition raid = config.scheduledRaids.get(raidIndex);
        if (raid.waves == null) {
            raid.waves = new ArrayList<>();
        }

        ConfigBuilder builder = createBaseBuilder(parent, Component.literal("Waves: " + raid.name));
        ConfigEntryBuilder eb = builder.entryBuilder();
        ConfigCategory cat = builder.getOrCreateCategory(Component.literal("Waves"));

        cat.addEntry(new ClothButtonEntry(
                Component.literal("Create Wave:"),
                Component.literal("\u00a7a\u00a7l\u2795 Add New Wave"),
                180,
                () -> {
                    ConfigManager.save();
                    ModConfig.WaveConfig newWave = new ModConfig.WaveConfig(raid.waves.size() + 1);
                    newWave.name = "Wave " + (raid.waves.size() + 1);
                    newWave.delayBeforeWaveSeconds = 5;
                    newWave.startMessage = "Wave " + (raid.waves.size() + 1) + " approaching!";
                    newWave.clearMessage = "Wave " + (raid.waves.size() + 1) + " cleared!";
                    newWave.mobs = new ArrayList<>();
                    newWave.mobs.add(new ModConfig.MobEntry("minecraft:zombie", 4));
                    raid.waves.add(newWave);
                    ConfigManager.save();
                    Minecraft.getInstance().setScreen(buildWavesListScreen(parent, raidIndex, rootParent, tabName));
                }
        ));

        for (int w = 0; w < raid.waves.size(); w++) {
            final int waveIdx = w;
            ModConfig.WaveConfig wave = raid.waves.get(w);
            int mobCount = (wave.mobs != null) ? wave.mobs.size() : 0;
            String waveDisplayName = (wave.name != null && !wave.name.trim().isEmpty()) ? wave.name.trim() : ("Wave " + (waveIdx + 1));
            String waveLabel = waveDisplayName + " (" + mobCount + " Mobs, " + wave.delayBeforeWaveSeconds + "s delay)";

            cat.addEntry(new ClothButtonEntry(
                    Component.literal("\u00a7e" + waveLabel + ":"),
                    Component.literal("\u00a7b\u00a7l\u2694 " + waveDisplayName + " Settings >"),
                    180,
                    () -> {
                        ConfigManager.save();
                        Minecraft.getInstance().setScreen(buildWaveDetailScreen(buildWavesListScreen(parent, raidIndex, rootParent, tabName), raidIndex, waveIdx, rootParent, tabName));
                    }
            ));

            cat.addEntry(new ClothButtonEntry(
                    Component.literal("Delete " + waveDisplayName + ":"),
                    Component.literal("\u00a7c\ud83d\uddd1 Delete Wave"),
                    140,
                    () -> {
                        ConfigManager.save();
                        if (waveIdx >= 0 && waveIdx < raid.waves.size()) {
                            raid.waves.remove(waveIdx);
                            ConfigManager.save();
                        }
                        Minecraft.getInstance().setScreen(buildWavesListScreen(parent, raidIndex, rootParent, tabName));
                    }
            ));

            cat.addEntry(eb.startTextDescription(Component.literal("----------------------------------------")).build());
        }

        cat.addEntry(eb.startTextDescription(Component.literal(" ")).build());
        cat.addEntry(eb.startTextDescription(Component.literal(" ")).build());

        return builder.build();
    }

    // =========================================================================
    // SUB-SCREEN: WAVE DETAIL (Settings & button to configure mobs)
    // =========================================================================
    public static Screen buildWaveDetailScreen(Screen parent, int raidIndex, int waveIndex, Screen rootParent, String tabName) {
        ModConfig config = ConfigManager.getConfig();
        if (raidIndex < 0 || raidIndex >= config.scheduledRaids.size()) {
            return parent;
        }

        ModConfig.RaidDefinition raid = config.scheduledRaids.get(raidIndex);
        if (waveIndex < 0 || waveIndex >= raid.waves.size()) {
            return parent;
        }

        ModConfig.WaveConfig wave = raid.waves.get(waveIndex);
        if (wave.mobs == null) {
            wave.mobs = new ArrayList<>();
        }

        String waveTitle = (wave.name != null && !wave.name.trim().isEmpty()) ? wave.name.trim() : ("Wave " + (waveIndex + 1));

        ConfigBuilder builder = createBaseBuilder(parent, Component.literal(waveTitle + " Settings"));
        ConfigEntryBuilder eb = builder.entryBuilder();
        ConfigCategory cat = builder.getOrCreateCategory(Component.literal("Wave Settings"));

        // 1. Wave Name (Optional)
        cat.addEntry(eb.startStrField(Component.literal("Wave Name (Optional)"), wave.name != null ? wave.name : "")
                .setDefaultValue("")
                .setSaveConsumer(val -> wave.name = val)
                .build());

        // 2. Delay Before Wave
        cat.addEntry(eb.startIntSlider(Component.literal("Delay Before Wave (Seconds)"), wave.delayBeforeWaveSeconds, 0, 60)
                .setDefaultValue(5)
                .setSaveConsumer(val -> wave.delayBeforeWaveSeconds = val)
                .build());

        // 3. Wave Start Message
        cat.addEntry(eb.startStrField(Component.literal("Wave Start Message"), wave.startMessage != null ? wave.startMessage : "")
                .setDefaultValue("Wave " + (waveIndex + 1) + " approaching!")
                .setSaveConsumer(val -> wave.startMessage = val)
                .build());

        // 4. Wave Clear Message
        cat.addEntry(eb.startStrField(Component.literal("Wave Clear Message"), wave.clearMessage != null ? wave.clearMessage : "")
                .setDefaultValue("Wave " + (waveIndex + 1) + " cleared!")
                .setSaveConsumer(val -> wave.clearMessage = val)
                .build());

        // 5. Configure Mobs Button
        int mobCount = wave.mobs.size();
        cat.addEntry(new ClothButtonEntry(
                Component.literal("Mobs in Wave (" + mobCount + " configured):"),
                Component.literal("\u00a7b\u00a7l\ud83d\udc7e Configure Mobs >"),
                200,
                () -> {
                    ConfigManager.save();
                    Minecraft.getInstance().setScreen(buildWaveMobsScreen(buildWaveDetailScreen(parent, raidIndex, waveIndex, rootParent, tabName), raidIndex, waveIndex));
                }
        ));

        cat.addEntry(eb.startTextDescription(
                Component.literal("\u00a7c--- Danger Zone ---")
        ).build());

        // 6. Delete Wave Button
        cat.addEntry(new ClothButtonEntry(
                Component.literal("Delete This Wave:"),
                Component.literal("\u00a7c\u00a7l\ud83d\uddd1 Delete Wave"),
                180,
                () -> {
                    ConfigManager.save();
                    if (waveIndex >= 0 && waveIndex < raid.waves.size()) {
                        raid.waves.remove(waveIndex);
                        ConfigManager.save();
                    }
                    Minecraft.getInstance().setScreen(parent);
                }
        ));

        cat.addEntry(eb.startTextDescription(Component.literal(" ")).build());
        cat.addEntry(eb.startTextDescription(Component.literal(" ")).build());

        return builder.build();
    }

    // =========================================================================
    // SUB-SCREEN: WAVE MOBS (List of mobs with count, reset, and small 🗑 button)
    // =========================================================================
    public static Screen buildWaveMobsScreen(Screen parent, int raidIndex, int waveIndex) {
        ModConfig config = ConfigManager.getConfig();
        if (raidIndex < 0 || raidIndex >= config.scheduledRaids.size()) {
            return parent;
        }

        ModConfig.RaidDefinition raid = config.scheduledRaids.get(raidIndex);
        if (waveIndex < 0 || waveIndex >= raid.waves.size()) {
            return parent;
        }

        ModConfig.WaveConfig wave = raid.waves.get(waveIndex);
        if (wave.mobs == null) {
            wave.mobs = new ArrayList<>();
        }

        String waveTitle = (wave.name != null && !wave.name.trim().isEmpty()) ? wave.name.trim() : ("Wave " + (waveIndex + 1));

        ConfigBuilder builder = createBaseBuilder(parent, Component.literal(waveTitle + " Mobs (" + wave.mobs.size() + ")"));
        ConfigEntryBuilder eb = builder.entryBuilder();
        ConfigCategory cat = builder.getOrCreateCategory(Component.literal("Mobs"));

        cat.addEntry(new ClothButtonEntry(
                Component.literal("Add Mob to Wave:"),
                Component.literal("\u00a7a\u00a7l\u2795 Add Hostile Mob (3D)"),
                220,
                () -> {
                    ConfigManager.save();
                    Screen mobsScreen = buildWaveMobsScreen(parent, raidIndex, waveIndex);
                    Minecraft.getInstance().setScreen(new MobPickerScreen(mobsScreen, pickedEntry -> {
                        wave.mobs.add(pickedEntry);
                        ConfigManager.save();
                        Minecraft.getInstance().setScreen(buildWaveMobsScreen(parent, raidIndex, waveIndex));
                    }));
                }
        ));

        cat.addEntry(eb.startTextDescription(
                Component.literal("\u00a76Mobs in " + waveTitle + ":")
        ).build());

        for (int m = 0; m < wave.mobs.size(); m++) {
            final int mobIdx = m;
            ModConfig.MobEntry mob = wave.mobs.get(m);

            cat.addEntry(new ClothMobEntry(mob, () -> {
                ConfigManager.save();
                if (mobIdx >= 0 && mobIdx < wave.mobs.size()) {
                    wave.mobs.remove(mobIdx);
                    ConfigManager.save();
                }
                Minecraft.getInstance().setScreen(buildWaveMobsScreen(parent, raidIndex, waveIndex));
            }));
        }

        cat.addEntry(eb.startTextDescription(Component.literal(" ")).build());
        cat.addEntry(eb.startTextDescription(Component.literal(" ")).build());

        return builder.build();
    }

    private static ModConfig.RaidDefinition createDefaultRaid(int index) {
        ModConfig.RaidDefinition raid = new ModConfig.RaidDefinition();
        raid.name = "Raid #" + index;
        raid.triggerDay = index * 3;
        raid.warningTime = "dusk";
        raid.warningMessage = "A hostile raid is targeting your base!";
        raid.victoryMessage = "Base defended successfully!";
        raid.defeatMessage = "Raid Failed! Base defenses collapsed.";
        raid.highlightRemainingMobs = true;
        raid.highlightThreshold = 3;
        raid.waves = new ArrayList<>();

        ModConfig.WaveConfig wave = new ModConfig.WaveConfig(1);
        wave.name = "Wave 1";
        wave.delayBeforeWaveSeconds = 5;
        wave.startMessage = "Wave 1 attacking!";
        wave.clearMessage = "Wave 1 eliminated!";
        wave.mobs = new ArrayList<>();
        wave.mobs.add(new ModConfig.MobEntry("minecraft:zombie", 4));
        raid.waves.add(wave);

        return raid;
    }
}
