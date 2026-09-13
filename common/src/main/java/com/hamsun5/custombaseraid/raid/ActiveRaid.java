package com.hamsun5.custombaseraid.raid;

import com.hamsun5.custombaseraid.Constants;
import com.hamsun5.custombaseraid.config.ConfigManager;
import com.hamsun5.custombaseraid.config.ModConfig;
import com.hamsun5.custombaseraid.mixin.MobAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ActiveRaid {

    public enum RaidState {
        NOT_STARTED,
        HUNT_COOLDOWN,
        ACTIVE_COMBAT,
        WAVE_INTERMISSION,
        VICTORY,
        DEFEAT,
        STOPPED
    }

    private final UUID raidId;
    private final ServerPlayer targetPlayer;
    private final ModConfig.RaidDefinition raidDef;
    private final BlockPos basePos;
    private final ServerLevel level;

    private RaidState state = RaidState.NOT_STARTED;
    private int currentWaveIndex = -1;
    private int totalMobsInCurrentWave = 0;
    private final List<Mob> aliveMobs = new ArrayList<>();

    private int huntCooldownTicks = 0;
    private int intermissionTicks = 0;
    private int maxIntermissionTicks = 1;
    private int timerTicks = 0;
    private long lastDayTime = 0;

    private ServerBossEvent bossBar;

    public ActiveRaid(ServerPlayer targetPlayer, ModConfig.RaidDefinition def, BlockPos basePos, ServerLevel level) {
        this.raidId = UUID.randomUUID();
        this.targetPlayer = targetPlayer;
        this.raidDef = def;
        this.basePos = basePos;
        this.level = level;
        this.lastDayTime = level.getDayTime();

        if (ConfigManager.getConfig().raidBossBar) {
            String raidName = (def.name != null && !def.name.isEmpty()) ? def.name : "Base Raid";
            this.bossBar = new ServerBossEvent(
                    Component.literal("§c§l" + raidName),
                    BossEvent.BossBarColor.BLUE,
                    BossEvent.BossBarOverlay.PROGRESS
            );
            this.bossBar.addPlayer(targetPlayer);
            this.bossBar.setProgress(1.0F);
            this.bossBar.setVisible(true);
        }
    }

    public void start() {
        this.state = RaidState.WAVE_INTERMISSION;
        this.currentWaveIndex = 0;

        ModConfig.WaveConfig wave0 = (raidDef.waves != null && !raidDef.waves.isEmpty()) ? raidDef.waves.get(0) : null;
        int initialDelaySecs = (wave0 != null && wave0.delayBeforeWaveSeconds > 0) ? wave0.delayBeforeWaveSeconds : 3;
        this.intermissionTicks = initialDelaySecs * 20;
        this.maxIntermissionTicks = this.intermissionTicks;

        String startMsg = (raidDef.warningMessage != null && !raidDef.warningMessage.isEmpty())
                ? raidDef.warningMessage
                : "The air grows cold... a raid is approaching!";
        sendChatMessage("§c§l[" + getRaidDisplayName() + " Started] §e" + startMsg);
        playSound(SoundEvents.RAID_HORN.value(), 2.0F, 1.0F);
        updateBossBar();
    }

    public void startWave(int waveIndex) {
        if (raidDef.waves == null || waveIndex >= raidDef.waves.size()) {
            finishVictory();
            return;
        }

        this.currentWaveIndex = waveIndex;
        ModConfig.WaveConfig wave = raidDef.waves.get(waveIndex);
        aliveMobs.clear();

        String waveName = (wave.name != null && !wave.name.isEmpty()) ? wave.name : ("Wave " + (waveIndex + 1) + "/" + raidDef.waves.size());
        String waveMsg = (wave.startMessage != null && !wave.startMessage.isEmpty())
                ? wave.startMessage
                : (waveName + " has spawned! (Hunting starts in " + raidDef.huntDelaySeconds + "s)");

        sendChatMessage("§c[" + getRaidDisplayName() + "] §e" + waveMsg);
        playSound(SoundEvents.WITHER_SPAWN, 1.0F, 1.2F);

        // Spawn mobs for this wave
        if (wave.mobs != null) {
            for (ModConfig.MobEntry entry : wave.mobs) {
                spawnMobGroup(entry);
            }
        }

        this.totalMobsInCurrentWave = aliveMobs.size();

        // Check if initial mob count is already <= threshold
        int threshold = Math.max(1, raidDef.highlightThreshold);
        if (raidDef.highlightRemainingMobs && aliveMobs.size() <= threshold) {
            for (Mob mob : aliveMobs) {
                if (mob != null && mob.isAlive()) {
                    mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 999999, 0, false, false));
                }
            }
        }

        this.huntCooldownTicks = Math.max(1, raidDef.huntDelaySeconds * 20);
        this.state = RaidState.HUNT_COOLDOWN;
        updateBossBar();
    }

    private void spawnMobGroup(ModConfig.MobEntry entry) {
        try {
            ResourceLocation typeId = ResourceLocation.parse(entry.mobId);
            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(typeId);

            if (entityType == null) {
                Constants.LOG.error("Unknown mob type in wave config: {}", entry.mobId);
                return;
            }

            int count = Math.max(1, entry.count);
            for (int i = 0; i < count; i++) {
                BlockPos spawnPos = findSpawnPosition();
                Entity entity = entityType.create(level);

                if (entity instanceof Mob mob) {
                    mob.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                    mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null);
                    mob.setPersistenceRequired();

                    level.addFreshEntity(mob);
                    aliveMobs.add(mob);

                    // Particle effect at spawn
                    level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                            spawnPos.getX() + 0.5, spawnPos.getY() + 0.5, spawnPos.getZ() + 0.5,
                            10, 0.5, 0.5, 0.5, 0.05);
                }
            }
        } catch (Exception e) {
            Constants.LOG.error("Failed to spawn mob entry {}: {}", entry.mobId, e.getMessage());
        }
    }

    private BlockPos findSpawnPosition() {
        int radius = raidDef.spawnRadius;
        double angle = level.random.nextDouble() * 2 * Math.PI;
        double dist = radius * 0.75 + level.random.nextDouble() * (radius * 0.25);

        int x = (int) (basePos.getX() + Math.cos(angle) * dist);
        int z = (int) (basePos.getZ() + Math.sin(angle) * dist);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

        return new BlockPos(x, y, z);
    }

    private void startHunting() {
        this.state = RaidState.ACTIVE_COMBAT;
        sendChatMessage("§4[" + getRaidDisplayName() + "] §cThe raiding mobs have locked onto your location and are hunting you down!");
        playSound(SoundEvents.ENDER_DRAGON_GROWL, 1.5F, 1.0F);

        for (Mob mob : aliveMobs) {
            if (mob != null && mob.isAlive()) {
                applyHuntAI(mob);
            }
        }
        updateBossBar();
    }

    private void applyHuntAI(Mob mob) {
        try {
            // 1. Boost follow range attribute so mob's native AI does not lose track of the player at long distance
            var followRangeAttr = mob.getAttribute(Attributes.FOLLOW_RANGE);
            if (followRangeAttr != null && followRangeAttr.getBaseValue() < 128.0) {
                followRangeAttr.setBaseValue(128.0);
            }

            // 2. Add targeting goal without line-of-sight requirement to targetSelector (NOT goalSelector)
            // This leaves the mob's attack goals, movement goals, and AI mod compatibility completely intact!
            if (mob instanceof MobAccessor accessor) {
                GoalSelector targetSelector = accessor.custombaseraid$getTargetSelector();
                targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(mob, ServerPlayer.class, false));
            }

            // 3. Directly assign target to the raided player
            mob.setTarget(targetPlayer);

            // 4. Provide an initial navigation path towards the player position
            mob.getNavigation().moveTo(targetPlayer, 1.25);
        } catch (Exception e) {
            Constants.LOG.warn("Could not apply Hunt targeting to {}: {}", mob.getType(), e.getMessage());
        }
    }

    public void tick() {
        if (state == RaidState.VICTORY || state == RaidState.DEFEAT || state == RaidState.STOPPED) {
            return;
        }

        // Check fail on player death
        if (raidDef.failOnPlayerDeath && (!targetPlayer.isAlive() || targetPlayer.isRemoved())) {
            finishDefeat("You were slain during the raid!");
            return;
        }

        // Track Minecraft in-game day time elapsed
        long currentDayTime = level.getDayTime();
        long dayTimeDelta = currentDayTime - lastDayTime;
        lastDayTime = currentDayTime;
        if (dayTimeDelta < 0) dayTimeDelta = 1; // Handled /time set backwards or wrap
        int ticksPassed = (int) Math.max(1, dayTimeDelta);

        timerTicks += ticksPassed;

        // Check time limit
        if (raidDef.enableTimeLimit) {
            int maxTicks = raidDef.timeLimitSeconds * 20;
            if (timerTicks >= maxTicks) {
                finishDefeat("Time limit exceeded! Base defenses collapsed.");
                return;
            }
        }

        switch (state) {
            case WAVE_INTERMISSION -> {
                intermissionTicks -= ticksPassed;
                if (intermissionTicks <= 0) {
                    startWave(currentWaveIndex);
                }
            }
            case HUNT_COOLDOWN -> {
                huntCooldownTicks -= ticksPassed;
                if (huntCooldownTicks <= 0) {
                    startHunting();
                }
            }
            case ACTIVE_COMBAT -> {
                // Prune dead mobs
                aliveMobs.removeIf(mob -> mob == null || !mob.isAlive() || mob.isRemoved());

                // Check remaining mob highlight threshold
                int threshold = Math.max(1, raidDef.highlightThreshold);
                if (raidDef.highlightRemainingMobs && aliveMobs.size() <= threshold) {
                    for (Mob mob : aliveMobs) {
                        if (mob != null && mob.isAlive() && !mob.hasEffect(MobEffects.GLOWING)) {
                            mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 999999, 0, false, false));
                        }
                    }
                }

                // Check periodic targeting reinforcement and guide idle mobs
                if (level.getGameTime() % 20 == 0) {
                    for (Mob mob : aliveMobs) {
                        if (mob != null && mob.isAlive()) {
                            // Ensure target is still the player
                            if (mob.getTarget() != targetPlayer) {
                                mob.setTarget(targetPlayer);
                            }
                            // If mob is currently idle (no path) and far away from player (> 16 blocks),
                            // nudge it with the player's current position so it doesn't get stuck.
                            // If within 16 blocks, do not touch navigation so vanilla attack goals
                            // (creeper swell, skeleton bow strafe, zombie melee, modded abilities) have 100% control!
                            if (mob.getNavigation().isDone() && mob.distanceToSqr(targetPlayer) > 256.0) {
                                mob.getNavigation().moveTo(targetPlayer, 1.25);
                            }
                        }
                    }
                }

                // Check if wave is cleared
                if (aliveMobs.isEmpty()) {
                    onWaveCleared();
                }
            }
            default -> {}
        }

        // Keep boss bar timer synchronized each tick
        if (state != RaidState.NOT_STARTED) {
            updateBossBar();
        }
    }

    private void onWaveCleared() {
        ModConfig.WaveConfig wave = raidDef.waves.get(currentWaveIndex);
        String clearMsg = (wave.clearMessage != null && !wave.clearMessage.isEmpty())
                ? wave.clearMessage
                : ("Wave " + (currentWaveIndex + 1) + " cleared!");

        sendChatMessage("§a[" + getRaidDisplayName() + "] §6" + clearMsg);
        playSound(SoundEvents.PLAYER_LEVELUP, 1.0F, 1.2F);

        int nextWave = currentWaveIndex + 1;
        if (nextWave < raidDef.waves.size()) {
            this.state = RaidState.WAVE_INTERMISSION;
            this.currentWaveIndex = nextWave;
            ModConfig.WaveConfig nextWaveConfig = raidDef.waves.get(nextWave);
            int delay = Math.max(1, nextWaveConfig.delayBeforeWaveSeconds);
            this.intermissionTicks = delay * 20;
            this.maxIntermissionTicks = this.intermissionTicks;
            sendChatMessage("§eNext wave arriving in " + delay + " seconds...");
            updateBossBar();
        } else {
            finishVictory();
        }
    }

    private void updateBossBar() {
        if (bossBar == null) return;

        ModConfig.WaveConfig wave = (raidDef.waves != null && currentWaveIndex >= 0 && currentWaveIndex < raidDef.waves.size())
                ? raidDef.waves.get(currentWaveIndex)
                : null;
        String waveLabel = (wave != null && wave.name != null && !wave.name.isEmpty())
                ? wave.name
                : ("Wave " + (currentWaveIndex + 1) + "/" + (raidDef.waves != null ? raidDef.waves.size() : 1));

        String timeLimitStr = "";
        if (raidDef.enableTimeLimit) {
            int maxTicks = raidDef.timeLimitSeconds * 20;
            int remainingTicks = Math.max(0, maxTicks - timerTicks);
            int totalSecs = (remainingTicks + 19) / 20;
            int mins = totalSecs / 60;
            int secs = totalSecs % 60;
            timeLimitStr = String.format(" | ⏱ %02d:%02d", mins, secs);
        }

        if (state == RaidState.WAVE_INTERMISSION) {
            bossBar.setColor(BossEvent.BossBarColor.BLUE);
            // Wave has not spawned yet: display countdown to wave start, NO mob count
            int countdownSecs = Math.max(0, (intermissionTicks + 19) / 20);
            float progress = maxIntermissionTicks > 0
                    ? Math.max(0.0F, Math.min(1.0F, (float) intermissionTicks / (float) maxIntermissionTicks))
                    : 1.0F;
            bossBar.setProgress(progress);
            String title = "§c§l" + getRaidDisplayName() + " §7[" + waveLabel + " - Starting in " + countdownSecs + "s" + timeLimitStr + "]";
            bossBar.setName(Component.literal(title));
        } else if (state == RaidState.HUNT_COOLDOWN) {
            bossBar.setColor(BossEvent.BossBarColor.YELLOW);
            // Wave spawned: show remaining mobs and hunt countdown
            float progress = totalMobsInCurrentWave > 0
                    ? Math.max(0.0F, Math.min(1.0F, (float) aliveMobs.size() / (float) totalMobsInCurrentWave))
                    : 0.0F;
            bossBar.setProgress(progress);
            int huntSecs = Math.max(0, (huntCooldownTicks + 19) / 20);
            String title = "§c§l" + getRaidDisplayName() + " §7[" + waveLabel + " - " + aliveMobs.size() + " Left (Hunt in " + huntSecs + "s)" + timeLimitStr + "]";
            bossBar.setName(Component.literal(title));
        } else if (state == RaidState.ACTIVE_COMBAT) {
            bossBar.setColor(BossEvent.BossBarColor.RED);
            // Active combat: show remaining mobs
            float progress = totalMobsInCurrentWave > 0
                    ? Math.max(0.0F, Math.min(1.0F, (float) aliveMobs.size() / (float) totalMobsInCurrentWave))
                    : 0.0F;
            bossBar.setProgress(progress);
            String title = "§c§l" + getRaidDisplayName() + " §7[" + waveLabel + " - " + aliveMobs.size() + " Left" + timeLimitStr + "]";
            bossBar.setName(Component.literal(title));
        }
    }

    public void finishVictory() {
        this.state = RaidState.VICTORY;
        String vicMsg = (raidDef.victoryMessage != null && !raidDef.victoryMessage.isEmpty())
                ? raidDef.victoryMessage
                : "Victory! You defended your base successfully!";
        sendChatMessage("§a§l[" + getRaidDisplayName() + "] §6" + vicMsg);
        playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);

        // Grant rewards
        if (raidDef.enableRewards && targetPlayer.isAlive()) {
            grantRewards();
        }

        cleanup();
    }

    public void finishDefeat(String reason) {
        this.state = RaidState.DEFEAT;
        sendChatMessage("§4§l[" + getRaidDisplayName() + " DEFEAT] §c" + reason);
        playSound(SoundEvents.WITHER_DEATH, 1.0F, 0.7F);
        cleanup();
    }

    private void grantRewards() {
        if (raidDef.rewards.experiencePoints > 0) {
            targetPlayer.giveExperiencePoints(raidDef.rewards.experiencePoints);
            sendChatMessage("§a+ Received " + raidDef.rewards.experiencePoints + " EXP!");
        }

        for (ModConfig.ItemReward itemReward : raidDef.rewards.items) {
            try {
                ResourceLocation itemId = ResourceLocation.parse(itemReward.itemId);
                Item item = BuiltInRegistries.ITEM.get(itemId);
                if (item != null) {
                    ItemStack stack = new ItemStack(item, itemReward.count);
                    if (!targetPlayer.getInventory().add(stack)) {
                        targetPlayer.drop(stack, false);
                    }
                    sendChatMessage("§a+ Reward: " + itemReward.count + "x " + item.getDescription().getString());
                }
            } catch (Exception e) {
                Constants.LOG.error("Failed to grant reward item {}: {}", itemReward.itemId, e.getMessage());
            }
        }

        if (raidDef.rewards.runCommand != null && !raidDef.rewards.runCommand.trim().isEmpty()) {
            String cmd = raidDef.rewards.runCommand.trim().replace("@p", targetPlayer.getScoreboardName());
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            try {
                targetPlayer.server.getCommands().performPrefixedCommand(targetPlayer.server.createCommandSourceStack(), cmd);
            } catch (Exception e) {
                Constants.LOG.error("Failed to execute reward command: {}", cmd, e);
            }
        }
    }

    public void stop() {
        this.state = RaidState.STOPPED;
        cleanup();
    }

    private void cleanup() {
        if (bossBar != null) {
            bossBar.removeAllPlayers();
            bossBar.setVisible(false);
        }
        for (Mob mob : aliveMobs) {
            if (mob != null && mob.isAlive()) {
                mob.discard();
            }
        }
        aliveMobs.clear();
    }

    public boolean isFinished() {
        return state == RaidState.VICTORY || state == RaidState.DEFEAT || state == RaidState.STOPPED;
    }

    public UUID getRaidId() {
        return raidId;
    }

    public ServerPlayer getTargetPlayer() {
        return targetPlayer;
    }

    public RaidState getState() {
        return state;
    }

    private String getRaidDisplayName() {
        return (raidDef.name != null && !raidDef.name.isEmpty()) ? raidDef.name : "Base Raid";
    }

    private void sendChatMessage(String message) {
        if (targetPlayer != null && targetPlayer.connection != null) {
            targetPlayer.sendSystemMessage(Component.literal(message));
        }
    }

    private void playSound(SoundEvent sound, float volume, float pitch) {
        if (ConfigManager.getConfig().soundEffectsEnabled && targetPlayer != null && targetPlayer.connection != null) {
            targetPlayer.connection.send(new ClientboundSoundPacket(
                    BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
                    SoundSource.HOSTILE,
                    targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(),
                    volume, pitch, level.getRandom().nextLong()
            ));
        }
    }
}
