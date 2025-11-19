package com.adaptor.adaptor_fish;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Adaptor_fish implements ModInitializer {
    public static final String MOD_ID = "adaptor_fish";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Adaptor Fish initialized - Live fish will now fly towards you when fishing!");

        // 註冊鋤頭快速收穫功能
        CropHarvestHelper.register();
        LOGGER.info("Crop harvest helper registered - Right-click mature crops with a hoe to harvest and replant!");
    }
}
