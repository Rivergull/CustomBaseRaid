package com.hamsun5.custombaseraid.raid;

import com.hamsun5.custombaseraid.Constants;
import com.hamsun5.custombaseraid.config.ConfigManager;
import com.hamsun5.custombaseraid.config.ModConfig;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RaidManager {

    private static final Map<UUID, ActiveRaid> ACTIVE_RAIDS = new ConcurrentHashMap<>();
    private static final Set<String> TRIGGERED_RAIDS = new HashSet<>();
    private static final Set<String> WARNED_RAIDS = new HashSet<>();
    private static int lastRecordedDay = -1;

    public static final long RAID_START_TIME = 12000L; // Dusk (sunset)

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
        TRIGGERED_RAIDS.clear();
        WARNED_RAIDS.clear();
        lastRecordedDay = -1;
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

        // Ensure players exist in the level before evaluating daily schedule triggers
        if (level.players().isEmpty()) return;

        long currentDayTime = level.getDayTime();
        // 1-based day count (Day 1 is 0 - 23999 ticks, Day 2 is 24000 - 47999 ticks, etc.)
        int currentDay = (int) (currentDayTime / 24000L) + 1;
        long timeOfDay = Math.floorMod(currentDayTime, 24000L);

        // Reset daily trigger sets on new day
        if (currentDay != lastRecordedDay) {
            TRIGGERED_RAIDS.clear();
            WARNED_RAIDS.clear();
            lastRecordedDay = currentDay;
            Constants.LOG.info("CustomBaseRaid: In-game Day is now Day {} (DayTime: {})", currentDay, currentDayTime);
        }

        String mode = (config.raidScheduleMode != null) ? config.raidScheduleMode.toLowerCase().trim() : "scheduled";

        // 2. Check and trigger upcoming raid warnings
        checkAndTriggerWarnings(level, config, currentDay, timeOfDay, mode);

        // 3. Check and trigger scheduled raids at Dusk (12000 ticks)
        for (int i = 0; i < config.scheduledRaids.size(); i++) {
            ModConfig.RaidDefinition raidDef = config.scheduledRaids.get(i);
            String triggerKey = "day_" + currentDay + "_raid_" + i;

            if (TRIGGERED_RAIDS.contains(triggerKey)) {
                continue;
            }

            // Wait until Dusk (12000 ticks) for the raid to begin
            if (timeOfDay < RAID_START_TIME) {
                continue;
            }

            boolean shouldTrigger = isRaidDay(currentDay, raidDef, mode, level);

            // Mark this raid definition as evaluated for today so it doesn't trigger again today
            TRIGGERED_RAIDS.add(triggerKey);

            if (shouldTrigger) {
                Constants.LOG.info("CustomBaseRaid: Triggering scheduled raid '{}' on Day {} (timeOfDay: {})",
                        raidDef.name, currentDay, timeOfDay);
                triggerScheduledRaid(level, raidDef);
            }
        }
    }

    private static void checkAndTriggerWarnings(ServerLevel level, ModConfig config, int currentDay, long timeOfDay, String mode) {
        for (int i = 0; i < config.scheduledRaids.size(); i++) {
            ModConfig.RaidDefinition raidDef = config.scheduledRaids.get(i);
            String warnKey = "warn_day_" + currentDay + "_raid_" + i;

            if (WARNED_RAIDS.contains(warnKey)) {
                continue;
            }

            String warnSetting = getWarningTimeId(raidDef.warningTime);
            if ("disabled".equals(warnSetting) || "raid_start".equals(warnSetting)) {
                continue;
            }

            boolean isWarningDay = false;
            long targetWarnTime = 12000L;

            switch (warnSetting) {
                case "dusk_day_before" -> {
                    targetWarnTime = 12000L; // Dusk
                    isWarningDay = isDayBeforeRaid(currentDay, raidDef, mode, level);
                }
                case "dawn_day_before" -> {
                    targetWarnTime = 0L; // Dawn
                    isWarningDay = isDayBeforeRaid(currentDay, raidDef, mode, level);
                }
                case "dawn" -> {
                    targetWarnTime = 0L; // Dawn on raid day
                    isWarningDay = isRaidDay(currentDay, raidDef, mode, level);
                }
            }

            if (isWarningDay && timeOfDay >= targetWarnTime) {
                WARNED_RAIDS.add(warnKey);
                broadcastRaidWarning(level, raidDef, config);
            }
        }
    }

    private static void broadcastRaidWarning(ServerLevel level, ModConfig.RaidDefinition raidDef, ModConfig config) {
        String msg = (raidDef.warningMessage != null && !raidDef.warningMessage.trim().isEmpty())
                ? raidDef.warningMessage.trim()
                : "A hostile raid approaches your base! Prepare your defenses!";

        Constants.LOG.info("CustomBaseRaid: Broadcasting upcoming raid warning for '{}': {}", raidDef.name, msg);

        for (ServerPlayer player : level.players()) {
            if (!meetsAdvancementRequirement(player, raidDef)) {
                continue;
            }

            player.sendSystemMessage(Component.literal("\u00a76\u00a7l[Raid Warning] \u00a7e" + msg));
            if (config.soundEffectsEnabled) {
                player.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.2F, 0.8F);
            }
        }
    }

    public static boolean isRaidDay(int currentDay, ModConfig.RaidDefinition raidDef, String mode, ServerLevel level) {
        return switch (mode) {
            case "periodic" -> {
                int interval = Math.max(1, raidDef.triggerDay);
                yield (currentDay % interval == 0);
            }
            case "random" -> {
                int chance = Math.min(100, Math.max(1, raidDef.triggerDay));
                long daySeed = level.getSeed() + ((long) currentDay * 31L) + (long) raidDef.name.hashCode();
                Random dayRandom = new Random(daySeed);
                yield (dayRandom.nextInt(100) < chance);
            }
            default -> (raidDef.triggerDay == currentDay); // "scheduled"
        };
    }

    public static boolean isDayBeforeRaid(int currentDay, ModConfig.RaidDefinition raidDef, String mode, ServerLevel level) {
        return switch (mode) {
            case "periodic" -> {
                int interval = Math.max(1, raidDef.triggerDay);
                yield ((currentDay + 1) % interval == 0);
            }
            case "random" -> {
                int chance = Math.min(100, Math.max(1, raidDef.triggerDay));
                long nextDaySeed = level.getSeed() + ((long) (currentDay + 1) * 31L) + (long) raidDef.name.hashCode();
                Random dayRandom = new Random(nextDaySeed);
                yield (dayRandom.nextInt(100) < chance);
            }
            default -> {
                if (raidDef.triggerDay <= 1) {
                    yield (currentDay == 1);
                }
                yield (currentDay == raidDef.triggerDay - 1);
            }
        };
    }

    public static String getWarningTimeDescription(String warningTime) {
        if (warningTime == null) return "Dusk (Day Before)";
        String s = warningTime.trim().toLowerCase();
        return switch (s) {
            case "dusk_day_before", "dusk (day before)" -> "Dusk (Day Before)";
            case "dawn_day_before", "dawn (day before)" -> "Dawn (Day Before)";
            case "dawn", "dawn (day of raid)" -> "Dawn (Day of Raid)";
            case "raid_start", "at raid start (dusk)" -> "At Raid Start (Dusk)";
            case "disabled", "none" -> "Disabled";
            case "dusk" -> "At Raid Start (Dusk)";
            default -> "Dusk (Day Before)";
        };
    }

    public static String getWarningTimeId(String desc) {
        if (desc == null) return "dusk_day_before";
        if (desc.startsWith("Dusk (Day Before")) return "dusk_day_before";
        if (desc.startsWith("Dawn (Day Before")) return "dawn_day_before";
        if (desc.startsWith("Dawn (Day of Raid")) return "dawn";
        if (desc.startsWith("At Raid Start")) return "raid_start";
        if (desc.startsWith("Disabled")) return "disabled";
        return "dusk_day_before";
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

    public static long parseTimeToTicks(String timeStr) {
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
            // If player is reasonably close (within 128 blocks) to their bed/respawn anchor, spawn near base
            if (player.blockPosition().closerThan(respawnPos, 128.0)) {
                return respawnPos;
            }
        }
        return player.blockPosition();
    }
}
