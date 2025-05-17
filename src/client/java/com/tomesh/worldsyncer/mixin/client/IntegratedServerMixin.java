package com.tomesh.worldsyncer.mixin.client;

import com.tomesh.worldsyncer.ClientInit;
import com.tomesh.worldsyncer.GithubBackupMod;
import com.tomesh.worldsyncer.config.ModConfig;
import com.tomesh.worldsyncer.config.ModConfig.WorldBackupEntry;
import com.tomesh.worldsyncer.core.GithubService;

import net.minecraft.registry.RegistryKey; // Import RegistryKey
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.World; // Import World
import net.minecraft.world.level.storage.LevelStorage;
import net.minecraft.util.WorldSavePath;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;

import java.io.File;

@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {
    @Inject(method = "shutdown", at = @At("HEAD"))
    private void onServerShutdown(CallbackInfo ci) {
        ModConfig config = GithubBackupMod.getConfig();
        GithubService service = ClientInit.getGithubService();
        IntegratedServer server = (IntegratedServer) (Object) this;
        java.nio.file.Path worldFullPath = server.getSavePath(WorldSavePath.ROOT);
        String worldDirNameRaw = worldFullPath.getFileName().toString();
        String worldDirName = worldDirNameRaw;
        if (worldDirName.equals(".") || worldDirName.isEmpty()) {
            java.nio.file.Path parent = worldFullPath.getParent();
            if (parent != null) {
                worldDirName = parent.getFileName().toString();
            }
        }
        final String finalWorldDirName = worldDirName;
        String key = ModConfig.worldKey(finalWorldDirName);

        GithubBackupMod.LOGGER.info("[WorldSaver] Shutdown hook running for world '{}'. backedUpWorlds: {}",
                finalWorldDirName, config.backedUpWorlds);

        if (service == null) {
            GithubBackupMod.LOGGER.warn(
                    "IntegratedServerMixin: GithubService is null. Cannot backup world {} on shutdown.",
                    finalWorldDirName);
            return;
        }

        WorldBackupEntry entry = config.backedUpWorlds.get(key);
        if (entry != null && entry.enabled) {
            String repoFullName = entry.repoFullName;
            if (repoFullName == null || repoFullName.isEmpty() || !repoFullName.contains("/")) {
                GithubBackupMod.LOGGER.info("No valid repo for world '{}', creating private repo...",
                        finalWorldDirName);
                showSyncToast("World Saver: Starting sync for '" + finalWorldDirName + "'...");
                service.createPrivateRepoIfNotExists(finalWorldDirName)
                        .thenAccept(createdRepo -> {
                            if (createdRepo != null && !createdRepo.isEmpty()) {
                                config.backedUpWorlds.put(key, new WorldBackupEntry(createdRepo, true));
                                me.shedaniel.autoconfig.AutoConfig.getConfigHolder(ModConfig.class).save();
                                GithubBackupMod.LOGGER.info("Created and saved repo '{}' for world '{}'", createdRepo,
                                        finalWorldDirName);
                                service.backupWorld(worldFullPath.toFile(), createdRepo)
                                        .whenComplete((v, ex) -> {
                                            if (ex == null) {
                                                showSyncToast(
                                                        "World Saver: Sync complete for '" + finalWorldDirName + "'.");
                                            } else {
                                                showSyncToast(
                                                        "World Saver: Sync FAILED for '" + finalWorldDirName + "'.");
                                            }
                                        });
                            } else {
                                GithubBackupMod.LOGGER.error("Failed to create repo for world '{}', skipping backup.",
                                        finalWorldDirName);
                                showSyncToast("World Saver: Sync FAILED for '" + finalWorldDirName + "'.");
                            }
                        });
            } else {
                GithubBackupMod.LOGGER.info("Integrated server shutting down. World: {}. Backing up to GitHub repo: {}",
                        finalWorldDirName, repoFullName);
                showSyncToast("World Saver: Starting sync for '" + finalWorldDirName + "'...");
                service.backupWorld(worldFullPath.toFile(), repoFullName)
                        .whenComplete((v, ex) -> {
                            if (ex == null) {
                                showSyncToast("World Saver: Sync complete for '" + finalWorldDirName + "'.");
                            } else {
                                showSyncToast("World Saver: Sync FAILED for '" + finalWorldDirName + "'.");
                            }
                        });
            }
        }
    }

    private void showSyncToast(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.execute(() -> {
                if (client.player != null) {
                    client.player.sendMessage(net.minecraft.text.Text.literal(message), false);
                }
                if (client.getToastManager() != null) {
                    client.getToastManager().add(SystemToast.create(client, SystemToast.Type.PERIODIC_NOTIFICATION,
                            net.minecraft.text.Text.literal("World Saver"), net.minecraft.text.Text.literal(message)));
                }
            });
        }
    }
}