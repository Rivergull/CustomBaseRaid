package com.hamsun5.custombaseraid.client.gui;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.client.telemetry.WorldSessionTelemetryManager;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.ServerLinks;
import net.minecraft.world.Difficulty;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;

import java.time.Duration;
import java.util.Collections;
import java.util.OptionalLong;
import java.util.UUID;

public class DummyClientWorld {
    private static ClientLevel dummyLevel = null;

    public static ClientLevel getLevel() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            return mc.level;
        }

        if (dummyLevel == null) {
            try {
                Connection connection = new Connection(PacketFlow.CLIENTBOUND) {
                    @Override
                    public void send(net.minecraft.network.protocol.Packet<?> packet, PacketSendListener listener, boolean flush) {
                    }
                };

                GameProfile profile = new GameProfile(
                        mc.getUser() != null ? mc.getUser().getProfileId() : UUID.randomUUID(),
                        mc.getUser() != null ? mc.getUser().getName() : "Player"
                );

                WorldSessionTelemetryManager telemetryManager = mc.getTelemetryManager().createWorldSessionManager(
                        false,
                        Duration.ZERO,
                        null
                );

                CommonListenerCookie cookie = new CommonListenerCookie(
                        profile,
                        telemetryManager,
                        RegistryAccess.EMPTY.freeze(),
                        FeatureFlags.DEFAULT_FLAGS,
                        "vanilla",
                        null,
                        null,
                        Collections.emptyMap(),
                        null,
                        false,
                        Collections.emptyMap(),
                        ServerLinks.EMPTY
                );

                ClientPacketListener dummyPacketListener = new ClientPacketListener(mc, connection, cookie);

                ClientLevel.ClientLevelData levelData = new ClientLevel.ClientLevelData(
                        Difficulty.NORMAL,
                        false,
                        false
                );

                DimensionType defaultDim = new DimensionType(
                        OptionalLong.empty(),
                        true,
                        false,
                        false,
                        true,
                        1.0D,
                        true,
                        false,
                        -64,
                        384,
                        384,
                        net.minecraft.tags.BlockTags.INFINIBURN_OVERWORLD,
                        BuiltinDimensionTypes.OVERWORLD_EFFECTS,
                        0.0F,
                        new DimensionType.MonsterSettings(false, false, net.minecraft.util.valueproviders.UniformInt.of(0, 7), 0)
                );
                Holder<DimensionType> dimHolder = Holder.direct(defaultDim);

                dummyLevel = new ClientLevel(
                        dummyPacketListener,
                        levelData,
                        Level.OVERWORLD,
                        dimHolder,
                        1,
                        1,
                        () -> mc.getProfiler(),
                        mc.levelRenderer,
                        false,
                        0L
                );
            } catch (Throwable t) {
                // Non-critical fallback
            }
        }
        return dummyLevel;
    }
}
