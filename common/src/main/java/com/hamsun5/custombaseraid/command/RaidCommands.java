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
                source.sendSuccess(() -> Component.literal("§aStopped active raid for " + player.getName().getString()), true);
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
        source.sendSuccess(() -> Component.literal("§aAll active raids stopped!"), true);
        return 1;
    }

    private static int reloadConfig(CommandSourceStack source) {
        ConfigManager.load();
        source.sendSuccess(() -> Component.literal("§a[CustomBaseRaid] Config successfully reloaded!"), true);
        return 1;
    }

    private static int listRaids(CommandSourceStack source) {
        ModConfig config = ConfigManager.getConfig();
        source.sendSuccess(() -> Component.literal("§6§l=== Configured Raids (" + config.scheduledRaids.size() + ") ==="), false);
        for (int i = 0; i < config.scheduledRaids.size(); i++) {
            ModConfig.RaidDefinition raid = config.scheduledRaids.get(i);
            int idx = i + 1;
            source.sendSuccess(() -> Component.literal("§e" + idx + ". §f" + raid.name + " §7(Day " + raid.triggerDay + ", " + raid.warningTime + ") - " + raid.waves.size() + " Waves"), false);
        }
        return config.scheduledRaids.size();
    }

    private static int raidStatus(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            ActiveRaid raid = RaidManager.getActiveRaid(player.getUUID());
            if (raid != null) {
                source.sendSuccess(() -> Component.literal("§eRaid Status: §a" + raid.getState()), false);
                return 1;
            } else {
                source.sendSuccess(() -> Component.literal("§7No active raid currently running for you."), false);
                return 0;
            }
        }
        return 0;
    }
}
