package com.hamsun5.custombaseraid;

import com.hamsun5.custombaseraid.config.ConfigManager;
import com.hamsun5.custombaseraid.platform.Services;
import java.io.File;

public class CommonClass {

    public static void init() {
        // A nice log message so you know your mod is loading
        Constants.LOG.info("Initializing Custom Base Raids on {}!", Services.PLATFORM.getPlatformName());

        // Point the manager to the standard Minecraft config folder
        ConfigManager.init(new File("config"));
    }
}