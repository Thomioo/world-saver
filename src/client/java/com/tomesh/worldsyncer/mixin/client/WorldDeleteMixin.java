package com.tomesh.worldsyncer.mixin.client;

import net.minecraft.client.gui.screen.world.WorldListWidget;
import net.minecraft.world.level.storage.LevelSummary;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tomesh.worldsyncer.GithubBackupMod;
import com.tomesh.worldsyncer.config.ModConfig;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

@Mixin(WorldListWidget.WorldEntry.class)
public class WorldDeleteMixin {
    @Inject(method = "delete", at = @At("HEAD"))
    private void beforeDeleteWorld(CallbackInfo ci) {
        try {
            // Use reflection to access the private summary field
            LevelSummary summary = null;
            try {
                java.lang.reflect.Field summaryField = this.getClass().getSuperclass().getDeclaredField("levelSummary");
                summaryField.setAccessible(true);
                summary = (LevelSummary) summaryField.get(this);
            } catch (Exception e) {
                System.err.println("[WorldSyncer] Could not access LevelSummary via reflection: " + e.getMessage());
                return;
            }
            if (summary == null)
                return;
            String worldFolderName = summary.getName();
            String key = com.tomesh.worldsyncer.config.ModConfig.worldKey(worldFolderName);
            File savesDir = new File(MinecraftClient.getInstance().runDirectory, "saves");
            File worldDir = new File(savesDir, worldFolderName);
            // List of git-related files/dirs to delete
            File[] gitTargets = new File[] {
                    new File(worldDir, ".git"),
                    new File(worldDir, ".github"),
                    new File(worldDir, ".gitignore"),
                    new File(worldDir, ".gitattributes")
            };
            // Force GC to release JGit file locks before deletion
            System.gc();
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
            }
            for (File target : gitTargets) {
                boolean deleted = false;
                for (int i = 0; i < 5; i++) {
                    if (target.exists()) {
                        if (target.isDirectory()) {
                            deleted = deleteDirectoryNio(target.toPath());
                        } else {
                            deleted = target.delete();
                        }
                        if (!deleted && target.exists()) {
                            try {
                                Thread.sleep(200);
                            } catch (InterruptedException ignored) {
                            }
                        } else {
                            break;
                        }
                    } else {
                        deleted = true;
                        break;
                    }
                }
                if (!deleted && target.exists()) {
                    System.err
                            .println(
                                    "[WorldSyncer] Failed to delete git-related file/dir: " + target.getAbsolutePath());
                }
            }
            // Remove from backup config and add to noBackupWorlds
            ModConfig config = GithubBackupMod.getConfig();
            if (config != null) {
                ModConfig.WorldBackupEntry entry = config.getBackupEntry(worldFolderName);
                boolean hasGithubRepo = entry != null && entry.repoFullName != null && !entry.repoFullName.isEmpty();
                if (!hasGithubRepo) {
                    // Remove from both backedUpWorlds and noBackupWorlds
                    config.removeBackupEntry(worldFolderName);
                    config.removeNoBackupWorld(worldFolderName);
                } else {
                    config.addNoBackupWorld(worldFolderName);
                    config.removeBackupEntry(worldFolderName);
                }
                me.shedaniel.autoconfig.AutoConfig.getConfigHolder(ModConfig.class).save();
            }
        } catch (Exception e) {
            System.err.println("[WorldSyncer] Failed to clean up git files before world deletion: " + e.getMessage());
        }
    }

    private static boolean deleteDirectoryNio(Path dir) {
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    try {
                        // Remove read-only attribute if set
                        File f = file.toFile();
                        if (!f.canWrite())
                            f.setWritable(true);
                        Files.delete(file);
                    } catch (AccessDeniedException ade) {
                        // Try to unlock the file by opening and closing it
                        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
                            // Just open and close
                        } catch (Exception e) {
                            System.err.println("[WorldSyncer] Could not unlock file: " + file + " - " + e.getMessage());
                        }
                        try {
                            Files.delete(file);
                        } catch (IOException e2) {
                            System.err.println("[WorldSyncer] Still failed to delete file after unlock attempt: " + file
                                    + " - " + e2.getMessage());
                        }
                    } catch (IOException e) {
                        System.err.println("[WorldSyncer] Failed to delete file: " + file + " - " + e.getMessage());
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    try {
                        Files.delete(dir);
                    } catch (IOException e) {
                        System.err.println("[WorldSyncer] Failed to delete directory: " + dir + " - " + e.getMessage());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            return true;
        } catch (IOException e) {
            System.err.println("[WorldSyncer] NIO delete failed: " + e.getMessage());
            return false;
        }
    }

    private static boolean deleteFileWithUnlock(File file) {
        try {
            if (!file.canWrite())
                file.setWritable(true);
            if (file.delete())
                return true;
        } catch (SecurityException ignored) {
        }
        // Try to unlock by opening and closing
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            // Just open and close
        } catch (Exception e) {
            System.err.println("[WorldSyncer] Could not unlock .gitignore: " + file + " - " + e.getMessage());
        }
        try {
            return file.delete();
        } catch (SecurityException e) {
            System.err.println("[WorldSyncer] Still failed to delete .gitignore after unlock attempt: " + file + " - "
                    + e.getMessage());
            return false;
        }
    }
}