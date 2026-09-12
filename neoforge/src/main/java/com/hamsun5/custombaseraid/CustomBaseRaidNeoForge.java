package com.hamsun5.custombaseraid;

import com.hamsun5.custombaseraid.client.gui.ClothConfigScreenBuilder;
import com.hamsun5.custombaseraid.command.RaidCommands;
import com.hamsun5.custombaseraid.raid.RaidManager;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@Mod(Constants.MOD_ID)
public class CustomBaseRaidNeoForge {

    public CustomBaseRaidNeoForge(IEventBus eventBus, ModContainer modContainer) {
        Constants.LOG.info("Initializing Custom Base Raid on NeoForge!");
        CommonClass.init();

        // Register event listeners on NeoForge event bus
        NeoForge.EVENT_BUS.register(this);

        // Register Config Screen on client side using Cloth Config
        if (FMLEnvironment.dist.isClient()) {
            modContainer.registerExtensionPoint(IConfigScreenFactory.class, (container, parent) -> ClothConfigScreenBuilder.build(parent));
        }
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            RaidManager.onServerTick(serverLevel);
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        RaidManager.onServerStopping();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        RaidCommands.register(event.getDispatcher());
    }
}
