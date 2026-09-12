package com.hamsun5.custombaseraid.raid;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class HuntPlayerGoal extends Goal {

    private final Mob mob;
    private final ServerPlayer targetPlayer;
    private final double speedModifier;
    private int recalculatePathTicks;

    public HuntPlayerGoal(Mob mob, ServerPlayer targetPlayer, double speedModifier) {
        this.mob = mob;
        this.targetPlayer = targetPlayer;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));

        // Boost follow range so the mob doesn't lose track of player at long range
        var followRangeAttr = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRangeAttr != null && followRangeAttr.getBaseValue() < 128.0) {
            followRangeAttr.setBaseValue(128.0);
        }
    }

    @Override
    public boolean canUse() {
        if (targetPlayer == null || !targetPlayer.isAlive() || targetPlayer.isSpectator()) {
            return false;
        }
        if (!mob.isAlive()) {
            return false;
        }
        return mob.level().dimension().equals(targetPlayer.level().dimension());
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        this.mob.setTarget(this.targetPlayer);
        this.mob.getNavigation().moveTo(this.targetPlayer, this.speedModifier);
        this.recalculatePathTicks = 0;
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.targetPlayer == null) return;

        if (this.mob.getTarget() != this.targetPlayer) {
            this.mob.setTarget(this.targetPlayer);
        }

        this.mob.getLookControl().setLookAt(this.targetPlayer, 30.0F, 30.0F);

        if (--this.recalculatePathTicks <= 0) {
            this.recalculatePathTicks = 15;
            this.mob.getNavigation().moveTo(this.targetPlayer, this.speedModifier);
        }
    }
}
