// src/main/java/com/yourname/githubbackup/GithubBackupMod.java
package com.tomesh.worldsyncer;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tomesh.worldsyncer.config.ModConfig;

public class GithubBackupMod implements ModInitializer {
    public static final String MOD_ID = "world-syncer"; // Or your actual mod ID from fabric.mod.json
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static ModConfig config;

    @Override
    public void onInitialize() {
        AutoConfig.register(ModConfig.class, GsonConfigSerializer::new);
        config = AutoConfig.getConfigHolder(ModConfig.class).getConfig();
        LOGGER.info("GitHub Backup Mod Initialized! (Config Loaded)");

        // The GithubService instance will be managed on the client side (e.g., in
        // ClientInit)
    }

    public static ModConfig getConfig() {
        if (config == null) { // Should not happen if onInitialize ran
            config = AutoConfig.getConfigHolder(ModConfig.class).getConfig();
        }
        return config;
    }

    // Remove getGithubService() from here
}