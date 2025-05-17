package com.tomesh.worldsyncer.mixin.client;

import com.tomesh.worldsyncer.ClientInit;
import com.tomesh.worldsyncer.GithubBackupMod;
import com.tomesh.worldsyncer.config.ModConfig;
import com.tomesh.worldsyncer.config.ModConfig.WorldBackupEntry;
import com.tomesh.worldsyncer.core.GithubService;

import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.screen.world.WorldListWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.world.level.storage.LevelSummary;

import java.io.File;
import java.nio.file.Path;

@Mixin(SelectWorldScreen.class)
public abstract class SelectWorldScreenMixin extends Screen {

    protected SelectWorldScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void githubBackup_onInitAndReload(CallbackInfo ci) {
        ModConfig config = GithubBackupMod.getConfig();
        GithubService service = ClientInit.getGithubService(); // Get service from ClientInit

        if (service == null) {
            GithubBackupMod.LOGGER.warn("SelectWorldScreenMixin: GithubService is null. Skipping auto-pull/clone.");
            return;
        }

        if (config.githubAccessToken == null || config.githubAccessToken.isEmpty()) {
            GithubBackupMod.LOGGER
                    .info("SelectWorldScreen: GitHub Backup not configured (no token). Skipping auto-pull/clone.");
            return;
        }

        GithubBackupMod.LOGGER
                .info("SelectWorldScreen initialized/reloaded. Checking for updates for backed up worlds.");
        Path savesDir = GithubService.getSavesDir(); // Static method in GithubService is fine

        config.backedUpWorlds.forEach((worldDirName, entry) -> {
            if (entry != null && entry.enabled && entry.repoFullName != null && !entry.repoFullName.isEmpty()) {
                File worldFullPath = savesDir.resolve(worldDirName).toFile();
                service.pullWorld(worldFullPath, entry.repoFullName)
                        .exceptionally(ex -> {
                            GithubBackupMod.LOGGER.error("Exception during auto-pull/clone for world {}: {}",
                                    worldDirName, ex.getMessage(), ex);
                            return null;
                        });
            }
        });
    }
}