package com.hamsun5.custombaseraid.mixin;

import com.hamsun5.custombaseraid.raid.RaidManager;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayer {

    @Inject(method = "startSleepInBed", at = @At("HEAD"), cancellable = true)
    private void custombaseraid$preventSleepDuringRaid(BlockPos at, CallbackInfoReturnable<Either<Player.BedSleepingProblem, Unit>> cir) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (RaidManager.hasActiveRaid(player)) {
            player.displayClientMessage(Component.literal("\u00a7cYou cannot sleep now! There is an active raid happening!"), true);
            cir.setReturnValue(Either.left(Player.BedSleepingProblem.OTHER_PROBLEM));
        }
    }
}
