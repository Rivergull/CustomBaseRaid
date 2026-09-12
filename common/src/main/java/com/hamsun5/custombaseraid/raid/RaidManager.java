package com.hamsun5.custombaseraid.raid;

import com.hamsun5.custombaseraid.Constants;
import com.hamsun5.custombaseraid.config.ConfigManager;
import com.hamsun5.custombaseraid.config.ModConfig;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RaidManager {

    private static final Map<UUID, ActiveRaid> ACTIVE_RAIDS = new ConcurrentHashMap<>();
    private static final Set<String> TRIGGERED_RAIDS = new HashSet<>();
    private static int lastRecordedDay = -1;
    private static long lastCheckedDayTime = -1;

    public static boolean hasActiveRaid(ServerPlayer player) {
        if (player == null) return false;
        ActiveRaid raid = ACTIVE_RAIDS.get(player.getUUID());
        return raid != null && !raid.isFinished();
    }

    public static ActiveRaid getActiveRaid(UUID playerUUID) {
        if (playerUUID == null) return null;
        ActiveRaid raid = ACTIVE_RAIDS.get(playerUUID);
        return (raid != null && !raid.isFinished()) ? raid : null;
    }

    public static boolean stopActiveRaid(ServerPlayer player) {
        if (player == null) return false;
        ActiveRaid raid = ACTIVE_RAIDS.remove(player.getUUID());
        if (raid != null) {
            raid.stop();
            return true;
        }
        return false;
    }

    public static void onServerStopping() {
        stopAllRaids();
    }

    public static void onServerTick(ServerLevel level) {
        // Only run raid management in Overworld
        if (level.dimension() != Level.OVERWORLD) return;

        // 1. Tick all active raids
        for (ActiveRaid raid : ACTIVE_RAIDS.values()) {
            raid.tick();
        }
        ACTIVE_RAIDS.values().removeIf(ActiveRaid::isFinished);

        ModConfig config = ConfigManager.getConfig();
        if (!config.enableRaids || config.scheduledRaids == null || config.scheduledRaids.isEmpty()) return;

        long currentDayTime = level.getDayTime();
        int currentDay = (int) (currentDayTime / 24000L);

        // Reset daily trigger set on new day
        if (currentDay != lastRecordedDay) {
            TRIGGERED_RAIDS.clear();
            lastRecordedDay = currentDay;
        }

        if (lastCheckedDayTime < 0) {
            lastCheckedDayTime = currentDayTime;
        }

        long timeOfDay = currentDayTime % 24000L;
        String mode = (config.raidScheduleMode != null) ? config.raidScheduleMode.toLowerCase().trim() : "scheduled";

        for (int i = 0; i < config.scheduledRaids.size(); i++) {
            ModConfig.RaidDefinition raidDef = config.scheduledRaids.get(i);
            String triggerKey = "day_" + currentDay + "_raid_" + i;

            if (TRIGGERED_RAIDS.contains(triggerKey)) {
                continue;
            }

            long targetTimeOfDay = parseTimeToTicks(raidDef.warningTime);
            long targetAbsoluteTime = (long) currentDay * 24000L + targetTimeOfDay;
            boolean isTimeWindow = currentDayTime >= targetAbsoluteTime && lastCheckedDayTime <= targetAbsoluteTime + 400;

            if (!isTimeWindow) {
                continue;
            }

            switch (mode) {
                case "periodic" -> {
                    int interval = Math.max(1, raidDef.triggerDay);
                    if (currentDay > 0 && currentDay % interval == 0) {
                        TRIGGERED_RAIDS.add(triggerKey);
                        triggerScheduledRaid(level, raidDef);
                    }
                }
                case "random" -> {
                    TRIGGERED_RAIDS.add(triggerKey);
                    int chance = Math.min(100, Math.max(1, raidDef.triggerDay));
                    int roll = level.random.nextInt(100);
                    if (roll < chance) {
                        triggerScheduledRaid(level, raidDef);
                    }
                }
                default -> { // "scheduled"
                    if (raidDef.triggerDay == currentDay) {
                        TRIGGERED_RAIDS.add(triggerKey);
                        triggerScheduledRaid(level, raidDef);
                    }
                }
            }
        }

        lastCheckedDayTime = currentDayTime;
    }

    public static boolean meetsAdvancementRequirement(ServerPlayer player, ModConfig.RaidDefinition raidDef) {
        if (player == null || raidDef == null) return true;
        if (!raidDef.requireAdvancement || raidDef.requiredAdvancementId == null || raidDef.requiredAdvancementId.trim().isEmpty()) {
            return true;
        }

        ResourceLocation advId = ResourceLocation.tryParse(raidDef.requiredAdvancementId.trim());
        if (advId == null) return true;

        AdvancementHolder advHolder = player.server.getAdvancements().get(advId);
        if (advHolder == null) return true;

        return player.getAdvancements().getOrStartProgress(advHolder).isDone();
    }

    private static long parseTimeToTicks(String timeStr) {
        if (timeStr == null) return 12000L; // Default dusk
        String s = timeStr.trim().toLowerCase();
        return switch (s) {
            case "dawn", "sunrise" -> 0L;
            case "morning" -> 1000L;
            case "noon", "day" -> 6000L;
            case "afternoon" -> 9000L;
            case "dusk", "sunset" -> 12000L;
            case "night" -> 13000L;
            case "midnight" -> 18000L;
            default -> {
                try {
                    yield Long.parseLong(s);
                } catch (NumberFormatException e) {
                    yield 12000L;
                }
            }
        };
    }

    private static void triggerScheduledRaid(ServerLevel level, ModConfig.RaidDefinition raidDef) {
        for (ServerPlayer player : level.players()) {
            if (ACTIVE_RAIDS.containsKey(player.getUUID())) {
                continue; // Player already in a raid
            }

            // Check advancement condition
            if (!meetsAdvancementRequirement(player, raidDef)) {
                Constants.LOG.info("Skipping raid '{}' for player '{}' due to incomplete required advancement '{}'",
                        raidDef.name, player.getScoreboardName(), raidDef.requiredAdvancementId);
                continue;
            }

            BlockPos spawnPoint = resolvePlayerBasePosition(player, level);

            ActiveRaid raid = new ActiveRaid(player, raidDef, spawnPoint, level);
            ACTIVE_RAIDS.put(player.getUUID(), raid);
            raid.start();
        }
    }

    public static boolean startManualRaid(ServerPlayer player, int targetDay) {
        ModConfig config = ConfigManager.getConfig();
        ModConfig.RaidDefinition targetDef = null;

        for (ModConfig.RaidDefinition def : config.scheduledRaids) {
            if (def.triggerDay == targetDay) {
                targetDef = def;
                break;
            }
        }

        if (targetDef == null && !config.scheduledRaids.isEmpty()) {
            targetDef = config.scheduledRaids.get(0);
        }

        if (targetDef == null) {
            player.sendSystemMessage(Component.literal("\u00a7cNo raid definitions found in config!"));
            return false;
        }

        if (ACTIVE_RAIDS.containsKey(player.getUUID())) {
            player.sendSystemMessage(Component.literal("\u00a7cA raid is already active for you!"));
            return false;
        }

        ServerLevel level = player.serverLevel();
        BlockPos spawnPoint = resolvePlayerBasePosition(player, level);

        ActiveRaid raid = new ActiveRaid(player, targetDef, spawnPoint, level);
        ACTIVE_RAIDS.put(player.getUUID(), raid);
        raid.start();
        return true;
    }

    public static void stopAllRaids() {
        for (ActiveRaid raid : ACTIVE_RAIDS.values()) {
            raid.stop();
        }
        ACTIVE_RAIDS.clear();
    }

    private static BlockPos resolvePlayerBasePosition(ServerPlayer player, ServerLevel level) {
        BlockPos respawnPos = player.getRespawnPosition();
        if (respawnPos != null && player.getRespawnDimension() == level.dimension()) {
            return respawnPos;
        }
        return player.blockPosition();
    }
}
