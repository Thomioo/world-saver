package com.tomesh.worldsaver; // Assuming this is your base package

import com.tomesh.worldsaver.config.ModConfig; // Corrected import
import com.tomesh.worldsaver.config.ModConfig.WorldBackupEntry;
import com.tomesh.worldsaver.core.GithubService; // Corrected import
import me.shedaniel.autoconfig.AutoConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

import java.util.Map;

public class ClientInit implements ClientModInitializer {
    private static GithubService githubServiceInstance;

    @Override
    public void onInitializeClient() {
        GithubBackupMod.LOGGER.info("GitHub Backup Client Initializing..."); // Use the correct Mod ID's LOGGER
        ModConfig config = GithubBackupMod.getConfig();

        if (config != null && config.githubAccessToken != null) {
            githubServiceInstance = new GithubService(config.githubAccessToken);
        } else {
            GithubBackupMod.LOGGER
                    .warn("Initial config or token is null during ClientInit. GithubService not created yet.");
        }

        // Listen for config updates to re-initialize service if token changes
        if (config != null) {
            config.subscribeToUpdates(() -> {
                GithubBackupMod.LOGGER.info("Config updated on client. Re-initializing GithubService.");
                ModConfig newConfig = GithubBackupMod.getConfig();
                if (newConfig != null && newConfig.githubAccessToken != null) {
                    githubServiceInstance = new GithubService(newConfig.githubAccessToken);
                } else {
                    GithubBackupMod.LOGGER.warn("Config or token is null after update. GithubService not re-created.");
                    githubServiceInstance = null;
                }
            });
        } else {
            GithubBackupMod.LOGGER.error("ModConfig was null in ClientInit, cannot subscribe to updates.");
        }

        // After GithubService is initialized, scan for all Minecraft world repos and
        // update config and auto-clone as needed
        if (githubServiceInstance != null) {
            final boolean[] hasRun = { false };
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (!hasRun[0] && MinecraftClient.getInstance() != null
                        && MinecraftClient.getInstance().getLevelStorage() != null) {
                    hasRun[0] = true;
                    runWorldAutoConfigAndClone();
                }
            });
        }
    }

    /**
     * Scans for all Minecraft world repos on GitHub, updates config, and
     * auto-clones as needed.
     * Can be called on launch or after saving a new access token.
     */
    public static void runWorldAutoConfigAndClone() {
        if (githubServiceInstance == null) {
            GithubBackupMod.LOGGER.warn("runWorldAutoConfigAndClone: GithubService is null, skipping.");
            return;
        }
        new Thread(() -> {
            try {
                Map<String, String> remoteWorlds = githubServiceInstance.listMinecraftWorldRepos().get();
                java.nio.file.Path savesDir = com.tomesh.worldsaver.core.GithubService.getSavesDir();
                java.io.File savesDirFile = savesDir.toFile();
                if (!savesDirFile.exists())
                    savesDirFile.mkdirs();
                java.util.Set<String> localWorlds = new java.util.HashSet<>();
                for (java.io.File f : savesDirFile.listFiles()) {
                    if (f.isDirectory())
                        localWorlds.add(f.getName());
                }
                ModConfig configToUpdate = GithubBackupMod.getConfig();
                boolean configChanged = false;
                // Build a set of all world names present locally or on GitHub (exact, no
                // lowercasing)
                java.util.Set<String> allWorlds = new java.util.HashSet<>();
                for (String local : localWorlds) {
                    allWorlds.add(local);
                }
                for (String remote : remoteWorlds.keySet()) {
                    allWorlds.add(remote);
                }
                // Remove any config entries not present in either location
                java.util.Set<String> configKeysToRemove = new java.util.HashSet<>();
                for (String configKey : configToUpdate.backedUpWorlds.keySet()) {
                    if (!allWorlds.contains(configKey)) {
                        configKeysToRemove.add(configKey);
                    }
                }
                for (String removeKey : configKeysToRemove) {
                    configToUpdate.backedUpWorlds.remove(removeKey);
                    configChanged = true;
                }
                // Add all remote worlds to config if missing, default enabled=false
                for (Map.Entry<String, String> entry : remoteWorlds.entrySet()) {
                    String remoteWorld = entry.getKey();
                    String repoFullName = entry.getValue();
                    String configKey = ModConfig.worldKey(remoteWorld);
                    if (!configToUpdate.backedUpWorlds.containsKey(configKey)) {
                        configToUpdate.backedUpWorlds.put(configKey,
                                new WorldBackupEntry(repoFullName, false));
                        configChanged = true;
                    }
                }
                // For all config entries, if enabled, ensure local world exists (clone if
                // missing)
                for (Map.Entry<String, WorldBackupEntry> entry : configToUpdate.backedUpWorlds.entrySet()) {
                    String configKey = entry.getKey(); // exact folder name
                    WorldBackupEntry backupEntry = entry.getValue();
                    String realWorldName = configKey;
                    if (backupEntry.enabled) {
                        // If repoFullName is missing, create repo
                        if (backupEntry.repoFullName == null || backupEntry.repoFullName.isEmpty()) {
                            String createdRepo = githubServiceInstance
                                    .createPrivateRepoIfNotExists(realWorldName).get();
                            if (createdRepo != null && !createdRepo.isEmpty()) {
                                backupEntry.repoFullName = createdRepo;
                                configChanged = true;
                            }
                        }
                        // Only clone if not present locally (with original casing)
                        if (!localWorlds.contains(realWorldName) && backupEntry.repoFullName != null
                                && !backupEntry.repoFullName.isEmpty()) {
                            java.io.File worldDir = new java.io.File(savesDirFile, realWorldName);
                            githubServiceInstance.pullWorld(worldDir, backupEntry.repoFullName);
                        }
                    }
                }
                if (configChanged) {
                    me.shedaniel.autoconfig.AutoConfig.getConfigHolder(ModConfig.class).save();
                }
            } catch (Exception e) {
                GithubBackupMod.LOGGER.error("Error during auto-config/clone of worlds: {}",
                        e.getMessage(), e);
            }
        }, "WorldSaver-GitHubAutoConfig").start();
    }

    public static GithubService getGithubService() {
        if (githubServiceInstance == null) {
            GithubBackupMod.LOGGER.warn(
                    "GithubService was null when requested. Attempting re-initialization (if config is now available).");
            ModConfig config = GithubBackupMod.getConfig();
            if (config != null && config.githubAccessToken != null && !config.githubAccessToken.isEmpty()) {
                githubServiceInstance = new GithubService(config.githubAccessToken);
                if (githubServiceInstance == null) {
                    GithubBackupMod.LOGGER.error("Failed to create GithubService instance even with config.");
                }
            } else {
                GithubBackupMod.LOGGER
                        .error("Cannot initialize GithubService on demand: Config or token is still null/empty.");
            }
        }
        return githubServiceInstance;
    }

    /**
     * Removes any config entries for worlds that are not present locally or on
     * GitHub.
     * This is called on config screen open and on startup.
     */
    public static void cleanConfigOfNonexistentWorlds() {
        try {
            GithubBackupMod.LOGGER.info("Running config cleanup for non-existent worlds...");
            ModConfig configToUpdate = GithubBackupMod.getConfig();
            java.nio.file.Path savesDir = com.tomesh.worldsaver.core.GithubService.getSavesDir();
            java.io.File savesDirFile = savesDir.toFile();
            java.util.Set<String> localWorlds = new java.util.HashSet<>();
            if (savesDirFile.exists()) {
                for (java.io.File f : savesDirFile.listFiles()) {
                    if (f.isDirectory())
                        localWorlds.add(f.getName());
                }
            }
            // Try to get remote worlds if possible
            java.util.Set<String> remoteWorlds = new java.util.HashSet<>();
            try {
                if (githubServiceInstance != null) {
                    java.util.Map<String, String> remoteMap = githubServiceInstance.listMinecraftWorldRepos().get();
                    remoteWorlds.addAll(remoteMap.keySet());
                }
            } catch (Exception e) {
                GithubBackupMod.LOGGER.warn("Could not fetch remote worlds for config cleanup: {}", e.getMessage());
            }
            java.util.Set<String> allWorlds = new java.util.HashSet<>();
            for (String local : localWorlds) {
                allWorlds.add(local);
            }
            for (String remote : remoteWorlds) {
                allWorlds.add(remote);
            }
            java.util.Set<String> configKeysToRemove = new java.util.HashSet<>();
            for (String configKey : configToUpdate.backedUpWorlds.keySet()) {
                if (!allWorlds.contains(configKey)) {
                    configKeysToRemove.add(configKey);
                }
            }
            for (String removeKey : configKeysToRemove) {
                configToUpdate.backedUpWorlds.remove(removeKey);
            }
            if (!configKeysToRemove.isEmpty()) {
                me.shedaniel.autoconfig.AutoConfig.getConfigHolder(ModConfig.class).save();
                GithubBackupMod.LOGGER.info("Removed {} stale world(s) from config.", configKeysToRemove.size());
            }
        } catch (Exception e) {
            GithubBackupMod.LOGGER.error("Error during config cleanup: {}", e.getMessage(), e);
        }
    }
}