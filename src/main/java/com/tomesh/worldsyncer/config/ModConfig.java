package com.tomesh.worldsyncer.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.tomesh.worldsyncer.GithubBackupMod;

@Config(name = GithubBackupMod.MOD_ID)
public class ModConfig implements ConfigData {

    @ConfigEntry.Gui.Tooltip
    @Comment("Your GitHub Personal Access Token (PAT) with 'repo' scope.")
    public String githubAccessToken = "";

    /**
     * Map of world folder name to backup entry (repo info and enabled state).
     * If enabled is true, the world will be synced with GitHub.
     * If enabled is false, the world will not be synced, and .git/.gitignore will
     * be removed.
     */
    public Map<String, WorldBackupEntry> backedUpWorlds = new HashMap<>();

    // Worlds that have been deleted and should NOT be auto-restored from GitHub
    // until re-enabled in the config
    @ConfigEntry.Gui.Excluded
    public Set<String> noBackupWorlds = new HashSet<>();

    // Used to notify subscribers (like GithubService) when config changes
    @ConfigEntry.Gui.Excluded
    private transient List<Runnable> updateListeners = new ArrayList<>();

    public void subscribeToUpdates(Runnable listener) {
        if (updateListeners == null)
            updateListeners = new ArrayList<>(); // Handle deserialization
        updateListeners.add(listener);
    }

    @Override
    public void validatePostLoad() throws ValidationException {
        ConfigData.super.validatePostLoad();
        if (updateListeners == null)
            updateListeners = new ArrayList<>(); // Handle deserialization
        // Notify listeners that config has been loaded/changed
        for (Runnable listener : updateListeners) {
            listener.run();
        }
    }

    /**
     * Utility to get a world key (now just returns the name as-is)
     */
    public static String worldKey(String name) {
        return name;
    }

    /**
     * Get a backup entry by world name (case-sensitive, exact match)
     */
    public WorldBackupEntry getBackupEntry(String worldName) {
        return backedUpWorlds.get(worldKey(worldName));
    }

    /**
     * Add or update a backup entry (case-sensitive, exact match)
     */
    public void putBackupEntry(String worldName, WorldBackupEntry entry) {
        backedUpWorlds.put(worldKey(worldName), entry);
    }

    /**
     * Remove a backup entry (case-sensitive, exact match)
     */
    public void removeBackupEntry(String worldName) {
        backedUpWorlds.remove(worldKey(worldName));
    }

    /**
     * Add to noBackupWorlds (case-sensitive, exact match)
     */
    public void addNoBackupWorld(String worldName) {
        noBackupWorlds.add(worldKey(worldName));
    }

    /**
     * Remove from noBackupWorlds (case-sensitive, exact match)
     */
    public void removeNoBackupWorld(String worldName) {
        noBackupWorlds.remove(worldKey(worldName));
    }

    public static class WorldBackupEntry {
        public String repoFullName = "";
        public boolean enabled = false;

        public WorldBackupEntry() {
        }

        public WorldBackupEntry(String repoFullName, boolean enabled) {
            this.repoFullName = repoFullName;
            this.enabled = enabled;
        }
    }
}