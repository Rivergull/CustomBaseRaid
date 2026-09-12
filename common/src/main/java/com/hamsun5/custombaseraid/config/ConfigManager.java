package com.hamsun5.custombaseraid.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hamsun5.custombaseraid.Constants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

public class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File configFile;
    private static ModConfig config;

    public static void init(File configDir) {
        File modDir = new File(configDir, "custombaseraid");
        if (!modDir.exists()) {
            modDir.mkdirs();
        }
        configFile = new File(modDir, "config.json");
        load();
    }

    public static void load() {
        if (configFile != null && configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                config = GSON.fromJson(reader, ModConfig.class);
                if (config == null) {
                    config = new ModConfig();
                    save();
                } else {
                    sanitizeConfig(config);
                    save();
                }
            } catch (Exception e) {
                Constants.LOG.error("Failed to load config, regenerating defaults: {}", e.getMessage());
                config = new ModConfig();
                save();
            }
        } else {
            config = new ModConfig();
            if (configFile != null) {
                save();
            }
        }
    }

    private static void sanitizeConfig(ModConfig cfg) {
        if (cfg.scheduledRaids == null) {
            cfg.scheduledRaids = new ArrayList<>();
        }
        for (ModConfig.RaidDefinition raid : cfg.scheduledRaids) {
            if (raid.name == null || raid.name.isEmpty()) {
                raid.name = "Custom Raid (" + raid.triggerDay + "d)";
            }
            if (raid.warningTime == null) raid.warningTime = "dusk";
            if (raid.warningMessage == null) raid.warningMessage = "Your base is under attack!";
            if (raid.victoryMessage == null) raid.victoryMessage = "Victory! Base defended successfully!";
            if (raid.defeatMessage == null) raid.defeatMessage = "Raid Failed! Base defenses collapsed.";
            if (raid.spawnRadius < 5) raid.spawnRadius = 35;
            if (raid.timeLimitSeconds < 10) raid.timeLimitSeconds = 300;
            if (raid.huntDelaySeconds < 0) raid.huntDelaySeconds = 15;

            if (raid.rewards == null) {
                raid.rewards = new ModConfig.RewardSettings();
            }
            if (raid.rewards.items == null) {
                raid.rewards.items = new ArrayList<>();
            }
            if (raid.rewards.runCommand == null) {
                raid.rewards.runCommand = "";
            }
            if (raid.waves == null) {
                raid.waves = new ArrayList<>();
            }
            for (ModConfig.WaveConfig wave : raid.waves) {
                if (wave.startMessage == null) wave.startMessage = "";
                if (wave.clearMessage == null) wave.clearMessage = "";
                if (wave.mobs == null) {
                    wave.mobs = new ArrayList<>();
                }
                // Filter out non-existent / invalid mob IDs
                Iterator<ModConfig.MobEntry> mobIt = wave.mobs.iterator();
                while (mobIt.hasNext()) {
                    ModConfig.MobEntry mob = mobIt.next();
                    if (mob == null || mob.mobId == null || mob.mobId.trim().isEmpty()) {
                        mobIt.remove();
                        continue;
                    }
                    mob.mobId = mob.mobId.trim();
                    ResourceLocation res = ResourceLocation.tryParse(mob.mobId);
                    if (res == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(res)) {
                        Constants.LOG.warn("Pruning invalid / non-existent mob '{}' from config", mob.mobId);
                        mobIt.remove();
                        continue;
                    }
                    if (mob.count <= 0) mob.count = 1;
                }
            }
        }
    }

    public static void save() {
        if (configFile == null) return;
        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(config != null ? config : new ModConfig(), writer);
        } catch (IOException e) {
            Constants.LOG.error("Failed to save config: {}", e.getMessage());
        }
    }

    public static ModConfig getConfig() {
        if (config == null) {
            load();
        }
        return config;
    }
}
