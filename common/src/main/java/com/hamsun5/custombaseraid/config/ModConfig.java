package com.hamsun5.custombaseraid.config;

import java.util.ArrayList;
import java.util.List;

public class ModConfig {

    // Global Settings
    public boolean enableRaids = true;
    public int defaultHuntDelaySeconds = 15;
    public boolean soundEffectsEnabled = true;
    public boolean raidBossBar = true;
    public boolean failOnPlayerDeath = true;

    // Schedule Modes: "scheduled", "periodic", "random"
    public String raidScheduleMode = "scheduled";
    public int periodicIntervalDays = 3; // For periodic mode: triggers a raid every X days
    public int randomDailyChancePercent = 25; // For random mode: % chance each day to trigger a raid

    // Scheduled Raids List
    public List<RaidDefinition> scheduledRaids = new ArrayList<>();

    public ModConfig() {
        // Default raid setup
        RaidDefinition defaultRaid = new RaidDefinition();
        defaultRaid.name = "Base Raid";
        defaultRaid.triggerDay = 3;
        defaultRaid.warningTime = "dusk";
        defaultRaid.warningMessage = "The air grows cold... a small raiding party is approaching!";
        defaultRaid.victoryMessage = "Base Defended! The raiders have retreated!";
        defaultRaid.defeatMessage = "Raid Failed! Base defenses collapsed.";
        defaultRaid.spawnRadius = 32;
        defaultRaid.huntDelaySeconds = 15;
        defaultRaid.enableTimeLimit = true;
        defaultRaid.timeLimitSeconds = 180;
        defaultRaid.highlightRemainingMobs = true;
        defaultRaid.highlightThreshold = 3;
        defaultRaid.failOnPlayerDeath = true;
        defaultRaid.enableRewards = true;

        // Wave 1
        WaveConfig wave1 = new WaveConfig(1);
        wave1.name = "Wave 1";
        wave1.delayBeforeWaveSeconds = 5;
        wave1.startMessage = "Wave 1: Scout forces have arrived!";
        wave1.clearMessage = "Wave 1 cleared!";
        wave1.mobs.add(new MobEntry("minecraft:zombie", 4));
        wave1.mobs.add(new MobEntry("minecraft:skeleton", 2));
        defaultRaid.waves.add(wave1);

        // Wave 2
        WaveConfig wave2 = new WaveConfig(2);
        wave2.name = "Wave 2";
        wave2.delayBeforeWaveSeconds = 10;
        wave2.startMessage = "Wave 2: Heavy assault incoming!";
        wave2.clearMessage = "Wave 2 cleared!";
        wave2.mobs.add(new MobEntry("minecraft:zombie", 6));
        wave2.mobs.add(new MobEntry("minecraft:skeleton", 4));
        wave2.mobs.add(new MobEntry("minecraft:creeper", 1));
        defaultRaid.waves.add(wave2);

        // Wave 3
        WaveConfig wave3 = new WaveConfig(3);
        wave3.name = "Wave 3";
        wave3.delayBeforeWaveSeconds = 15;
        wave3.startMessage = "Final Wave: Boss assault incoming!";
        wave3.clearMessage = "Final wave eliminated!";
        wave3.mobs.add(new MobEntry("minecraft:zombie", 8));
        wave3.mobs.add(new MobEntry("minecraft:skeleton", 4));
        wave3.mobs.add(new MobEntry("minecraft:spider", 3));
        wave3.mobs.add(new MobEntry("minecraft:witch", 1));
        defaultRaid.waves.add(wave3);

        scheduledRaids.add(defaultRaid);
    }

    public static class RaidDefinition {
        public String name = "Base Raid";
        public int triggerDay = 3;
        public String warningTime = "dusk";
        public String warningMessage = "A hostile raid is targeting your base!";
        public String victoryMessage = "Base defended successfully!";
        public String defeatMessage = "Raid Failed! Base defenses collapsed.";
        public int spawnRadius = 32;
        public int huntDelaySeconds = 15;
        public boolean enableTimeLimit = true;
        public int timeLimitSeconds = 180;
        public boolean highlightRemainingMobs = true;
        public int highlightThreshold = 3;
        public boolean failOnPlayerDeath = true;
        public boolean requireAdvancement = false;
        public String requiredAdvancementId = "minecraft:story/mine_diamond";
        public boolean enableRewards = true;
        public RewardSettings rewards = new RewardSettings();
        public List<WaveConfig> waves = new ArrayList<>();
    }

    public static class RewardSettings {
        public int experiencePoints = 500;
        public String runCommand = "";
        public List<ItemReward> items = new ArrayList<>();

        public RewardSettings() {
            items.add(new ItemReward("minecraft:diamond", 2));
            items.add(new ItemReward("minecraft:emerald", 5));
            items.add(new ItemReward("minecraft:golden_apple", 1));
        }
    }

    public static class ItemReward {
        public String itemId;
        public int count;

        public ItemReward() {
            this("minecraft:diamond", 1);
        }

        public ItemReward(String itemId, int count) {
            this.itemId = itemId;
            this.count = count;
        }
    }

    public static class WaveConfig {
        public int waveNumber;
        public String name = "";
        public int delayBeforeWaveSeconds = 5;
        public String startMessage = "";
        public String clearMessage = "";
        public List<MobEntry> mobs = new ArrayList<>();

        public WaveConfig() {
            this(1);
        }

        public WaveConfig(int waveNumber) {
            this.waveNumber = waveNumber;
        }
    }

    public static class MobEntry {
        public String mobId;
        public int count;

        public MobEntry() {
            this("minecraft:zombie", 1);
        }

        public MobEntry(String mobId, int count) {
            this.mobId = mobId;
            this.count = count;
        }
    }
}
