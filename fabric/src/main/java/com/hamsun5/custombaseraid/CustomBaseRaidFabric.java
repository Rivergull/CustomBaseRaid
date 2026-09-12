package com.hamsun5.custombaseraid;

import com.hamsun5.custombaseraid.command.RaidCommands;
import com.hamsun5.custombaseraid.raid.RaidManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class CustomBaseRaidFabric implements ModInitializer {
    
    @Override
    public void onInitialize() {
        Constants.LOG.info("Initializing Custom Base Raid on Fabric!");
        CommonClass.init();

        // Register Server Tick Event for raids
        ServerTickEvents.END_WORLD_TICK.register(RaidManager::onServerTick);

        // Register Server Stopping for clean up
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> RaidManager.onServerStopping());

        // Register Commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            RaidCommands.register(dispatcher);
        });
    }
}
