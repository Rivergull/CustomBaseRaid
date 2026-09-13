package com.hamsun5.custombaseraid.command;

import com.hamsun5.custombaseraid.config.ConfigManager;
import com.hamsun5.custombaseraid.config.ModConfig;
import com.hamsun5.custombaseraid.raid.ActiveRaid;
import com.hamsun5.custombaseraid.raid.RaidManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class RaidCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("custombaseraid")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("start")
                        .executes(ctx -> startRaid(ctx.getSource(), -1))
                        .then(Commands.argument("day", IntegerArgumentType.integer(1))
                                .executes(ctx -> startRaid(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "day")))))
                .then(Commands.literal("stop")
                        .executes(ctx -> stopRaid(ctx.getSource())))
                .then(Commands.literal("stopall")
                        .executes(ctx -> stopAllRaids(ctx.getSource())))
                .then(Commands.literal("reload")
                        .executes(ctx -> reloadConfig(ctx.getSource())))
                .then(Commands.literal("list")
                        .executes(ctx -> listRaids(ctx.getSource())))
                .then(Commands.literal("status")
                        .executes(ctx -> raidStatus(ctx.getSource())))
        );

        // Alias /cbr
        dispatcher.register(Commands.literal("cbr")
                .requires(source -> source.hasPermission(2))
                .redirect(dispatcher.getRoot().getChild("custombaseraid")));
    }

    private static int startRaid(CommandSourceStack source, int day) {
        if (source.getEntity() instanceof ServerPlayer player) {
            boolean started = RaidManager.startManualRaid(player, day);
            return started ? 1 : 0;
        } else {
            source.sendFailure(Component.literal("Only players can trigger a base raid!"));
            return 0;
        }
    }

    private static int stopRaid(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            boolean stopped = RaidManager.stopActiveRaid(player);
            if (stopped) {
                source.sendSuccess(() -> Component.literal("\u00a7aStopped active raid for " + player.getName().getString()), true);
                return 1;
            } else {
                source.sendFailure(Component.literal("No active raid found for you."));
                return 0;
            }
        } else {
            source.sendFailure(Component.literal("Only players can stop their raid."));
            return 0;
        }
    }

    private static int stopAllRaids(CommandSourceStack source) {
        RaidManager.stopAllRaids();
        source.sendSuccess(() -> Component.literal("\u00a7aAll active raids stopped!"), true);
        return 1;
    }

    private static int reloadConfig(CommandSourceStack source) {
        ConfigManager.load();
        source.sendSuccess(() -> Component.literal("\u00a7a[CustomBaseRaid] Config successfully reloaded!"), true);
        return 1;
    }

    private static int listRaids(CommandSourceStack source) {
        ModConfig config = ConfigManager.getConfig();
        source.sendSuccess(() -> Component.literal("\u00a76\u00a7l=== Configured Raids (" + config.scheduledRaids.size() + ") ==="), false);
        for (int i = 0; i < config.scheduledRaids.size(); i++) {
            ModConfig.RaidDefinition raid = config.scheduledRaids.get(i);
            int idx = i + 1;
            String warningDesc = RaidManager.getWarningTimeDescription(raid.warningTime);
            source.sendSuccess(() -> Component.literal("\u00a7e" + idx + ". \u00a7f" + raid.name + " \u00a77(Day " + raid.triggerDay + ", Warning: " + warningDesc + ") - " + raid.waves.size() + " Waves"), false);
        }
        return config.scheduledRaids.size();
    }

    private static int raidStatus(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            ActiveRaid raid = RaidManager.getActiveRaid(player.getUUID());
            if (raid != null) {
                source.sendSuccess(() -> Component.literal("\u00a7eRaid Status: \u00a7a" + raid.getState()), false);
                return 1;
            } else {
                long currentDayTime = player.serverLevel().getDayTime();
                int currentDay = (int) (currentDayTime / 24000L) + 1;
                long timeOfDay = Math.floorMod(currentDayTime, 24000L);
                ModConfig config = ConfigManager.getConfig();

                source.sendSuccess(() -> Component.literal("\u00a77No active raid currently running for you."), false);
                source.sendSuccess(() -> Component.literal("\u00a7eCurrent World: \u00a7fDay " + currentDay + " \u00a77(Time: " + timeOfDay + " ticks - " + getTimeOfDayDescription(timeOfDay) + ")"), false);
                source.sendSuccess(() -> Component.literal("\u00a7eSchedule Mode: \u00a7f" + config.raidScheduleMode + " \u00a77(Raids Enabled: " + config.enableRaids + ")"), false);

                if (config.scheduledRaids != null && !config.scheduledRaids.isEmpty()) {
                    for (int i = 0; i < config.scheduledRaids.size(); i++) {
                        ModConfig.RaidDefinition r = config.scheduledRaids.get(i);
                        int finalI = i;
                        String warnDesc = RaidManager.getWarningTimeDescription(r.warningTime);
                        String dayInfo = switch (config.raidScheduleMode.toLowerCase().trim()) {
                            case "periodic" -> "Every " + r.triggerDay + " Days";
                            case "random" -> r.triggerDay + "% chance daily";
                            default -> "Day " + r.triggerDay;
                        };
                        source.sendSuccess(() -> Component.literal("\u00a77- Raid #" + (finalI + 1) + " ('" + r.name + "'): " + dayInfo + " (Warning: " + warnDesc + ", Attack at Dusk)"), false);
                    }
                }
                return 0;
            }
        }
        return 0;
    }

    private static String getTimeOfDayDescription(long timeOfDay) {
        if (timeOfDay < 1000) return "Dawn";
        if (timeOfDay < 6000) return "Morning";
        if (timeOfDay < 9000) return "Noon";
        if (timeOfDay < 12000) return "Afternoon";
        if (timeOfDay < 13000) return "Dusk";
        if (timeOfDay < 18000) return "Night";
        if (timeOfDay < 23000) return "Midnight";
        return "Pre-dawn";
    }
}
