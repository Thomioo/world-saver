package com.tomesh.worldsyncer.core; // Assuming your package is com.tomesh.worldsyncer.core

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.errors.EmptyCommitException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.URIish; // Import URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.kohsuke.github.GHCreateRepositoryBuilder;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.HttpException;

import com.tomesh.worldsyncer.GithubBackupMod;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException; // Import URISyntaxException
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.HashMap;
import java.util.Map;

public class GithubService {
    private String accessToken;
    private GitHub github;

    public GithubService(String token) {
        this.accessToken = token;
        if (token != null && !token.isEmpty()) {
            try {
                this.github = new GitHubBuilder().withOAuthToken(token).build();
                this.github.checkApiUrlValidity();
                GithubBackupMod.LOGGER.info("GitHub connection established with API.");
            } catch (IOException e) {
                GithubBackupMod.LOGGER.error("Failed to connect to GitHub with token: {}", e.getMessage());
                this.github = null;
                sendClientMessageAsync(
                        Text.literal("GitHub Backup: Failed to connect to GitHub API. Check token & internet.")
                                .formatted(Formatting.RED));
            }
        } else {
            this.github = null;
            GithubBackupMod.LOGGER.warn("GitHub access token is not set. Backups will not function.");
        }
    }

    private boolean isConfigured() {
        if (this.accessToken == null || this.accessToken.isEmpty()) {
            sendClientMessageAsync(Text.literal("GitHub Backup: Access token not configured in mod settings.")
                    .formatted(Formatting.RED));
            return false;
        }
        if (this.github == null) {
            if (this.accessToken != null && !this.accessToken.isEmpty()) {
                try {
                    this.github = new GitHubBuilder().withOAuthToken(this.accessToken).build();
                    this.github.checkApiUrlValidity();
                    GithubBackupMod.LOGGER.info("GitHub connection re-established.");
                } catch (IOException e) {
                    GithubBackupMod.LOGGER.error("Failed to re-connect to GitHub with token: {}", e.getMessage());
                    this.github = null;
                    sendClientMessageAsync(
                            Text.literal("GitHub Backup: GitHub connection not initialized. Check token & logs.")
                                    .formatted(Formatting.RED));
                    return false;
                }
            } else {
                sendClientMessageAsync(
                        Text.literal("GitHub Backup: GitHub connection not initialized and no token. Check logs.")
                                .formatted(Formatting.RED));
                return false;
            }
        }
        return true;
    }

    private void sendClientMessageAsync(Text message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.execute(() -> {
                if (client.player != null) {
                    client.player.sendMessage(message, false);
                } else {
                    GithubBackupMod.LOGGER.info("[GithubService PlayerMsg]: {}", message.getString());
                }
            });
        } else {
            GithubBackupMod.LOGGER.warn("[GithubService NoClient]: {}", message.getString());
        }
    }

    public CompletableFuture<String> createPrivateRepoIfNotExists(String worldName) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isConfigured()) {
                sendClientMessageAsync(
                        Text.literal("Cannot create repo: GitHub service not configured.").formatted(Formatting.RED));
                return null;
            }
            String repoName = "minecraft-world-" + worldName.replace(' ', '-');
            if (repoName.length() > 100) {
                repoName = repoName.substring(0, 100);
            }
            if (repoName.isEmpty() || repoName.equals("minecraft-world-")) {
                repoName = "minecraft-world-unnamed-" + System.currentTimeMillis() % 10000;
            }
            try {
                String ownerLogin = github.getMyself().getLogin();
                String fullRepoName = ownerLogin + "/" + repoName;
                try {
                    GHRepository existingRepo = github.getRepository(fullRepoName);
                    if (existingRepo != null) {
                        GithubBackupMod.LOGGER.info("Repository {} already exists.", fullRepoName);
                        sendClientMessageAsync(
                                Text.literal("Repo " + repoName + " already exists.").formatted(Formatting.YELLOW));
                        return existingRepo.getFullName();
                    }
                } catch (org.kohsuke.github.GHFileNotFoundException e) {
                    GithubBackupMod.LOGGER.info("Repository {} does not exist yet, attempting to create.",
                            fullRepoName);
                    // Repo does not exist, create it
                    try {
                        GHCreateRepositoryBuilder builder = github.createRepository(repoName)
                                .description("Automated backup for Minecraft world: " + worldName)
                                .private_(true).autoInit(false);
                        String createdRepoFullName = builder.create().getFullName();
                        GithubBackupMod.LOGGER.info("Successfully created private GitHub repository: {}",
                                createdRepoFullName);
                        sendClientMessageAsync(Text.literal("Created GitHub repo: " + createdRepoFullName)
                                .formatted(Formatting.GREEN));
                        return createdRepoFullName;
                    } catch (Exception ce) {
                        GithubBackupMod.LOGGER.error("Failed to create GitHub repository {}: {}", repoName,
                                ce.getMessage(), ce);
                        sendClientMessageAsync(Text.literal(
                                "Failed to create repo " + repoName + ". Error: " + ce.getMessage().split("\n")[0])
                                .formatted(Formatting.RED));
                        return null;
                    }
                }
                // If we get here, repo exists
                return fullRepoName;
            } catch (IOException e) {
                GithubBackupMod.LOGGER.error("Failed to create/check GitHub repository {}: {}", repoName,
                        e.getMessage(), e);
                sendClientMessageAsync(
                        Text.literal("Failed to create repo " + repoName + ". Error: " + e.getMessage().split("\n")[0])
                                .formatted(Formatting.RED));
                return null;
            }
        }, CompletableFuture.delayedExecutor(100, java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    public CompletableFuture<Void> backupWorld(File worldDir, String repoFullName) {
        return CompletableFuture.runAsync(() -> {
            if (!isConfigured() || repoFullName == null || repoFullName.isEmpty()) {
                sendClientMessageAsync(
                        Text.literal("Backup skipped for " + worldDir.getName() + ": Not configured or no repo name.")
                                .formatted(Formatting.YELLOW));
                return;
            }
            sendClientMessageAsync(Text.literal("Starting backup for " + worldDir.getName() + " to " + repoFullName)
                    .formatted(Formatting.AQUA));
            File gitDir = new File(worldDir, ".git");
            String remoteUrl = "https://github.com/" + repoFullName + ".git";
            Git git = null;
            try {
                if (!gitDir.exists() || !new File(gitDir, "config").exists()) {
                    GithubBackupMod.LOGGER.info("Initializing new Git repository in {}", worldDir.getAbsolutePath());
                    if (gitDir.exists()) {
                        deleteDirectory(gitDir);
                    }
                    git = Git.init().setDirectory(worldDir).setInitialBranch("main").call();
                    git.remoteAdd().setName("origin").setUri(new URIish(remoteUrl)).call();
                    GithubBackupMod.LOGGER.info("Added remote origin: {}", remoteUrl);
                    File gitignore = new File(worldDir, ".gitignore");
                    if (!gitignore.exists()) {
                        try (FileWriter writer = new FileWriter(gitignore)) {
                            writer.write(
                                    "session.lock\nlogs/\ncrash-reports/\n*.tmp\nicon.png\nlevel.dat_old\nplayerdata/*.dat_old\nadvancements/*.json_old\n");
                            GithubBackupMod.LOGGER.info("Created .gitignore in {}", worldDir.getAbsolutePath());
                        } catch (IOException e) {
                            GithubBackupMod.LOGGER.warn("Could not create .gitignore: {}", e.getMessage());
                        }
                    }
                    // Stage all files and make initial commit if HEAD does not exist
                    git.add().addFilepattern(".").call();
                    if (git.getRepository().resolve(org.eclipse.jgit.lib.Constants.HEAD) == null) {
                        git.commit().setMessage("Initial commit").setAuthor("Minecraft Backup Mod", "backup@mod.local")
                                .call();
                    }
                } else {
                    git = Git.open(worldDir);
                    var remotes = git.remoteList().call();
                    boolean originExists = remotes.stream().anyMatch(r -> r.getName().equals("origin"));
                    if (!originExists) {
                        git.remoteAdd().setName("origin").setUri(new URIish(remoteUrl)).call();
                        GithubBackupMod.LOGGER.info("Added missing remote origin: {}", remoteUrl);
                    } else if (remotes.stream().anyMatch(
                            r -> r.getName().equals("origin") && !r.getURIs().get(0).toString().equals(remoteUrl))) {
                        git.remoteSetUrl().setRemoteName("origin").setRemoteUri(new URIish(remoteUrl)).call();
                        GithubBackupMod.LOGGER.info("Updated remote origin URL to: {}", remoteUrl);
                    }
                    // Write .worldsyncer.json with real world name
                    File meta = new File(worldDir, ".worldsyncer.json");
                    try (FileWriter writer = new FileWriter(meta)) {
                        String canonicalName = worldDir.getCanonicalFile().getName();
                        writer.write("{\"realWorldName\":\"" + canonicalName.replace("\"", "\\\"") + "\"}");
                    } catch (IOException e) {
                        GithubBackupMod.LOGGER.warn("Could not write .worldsyncer.json: {}", e.getMessage());
                    }
                }
                GithubBackupMod.LOGGER.info("Staging changes for {}", worldDir.getName());
                git.add().addFilepattern(".").call();
                ObjectId head = git.getRepository().resolve(Constants.HEAD);
                if (git.status().call().isClean() && head != null) {
                    GithubBackupMod.LOGGER.info("No changes to commit for {}.", worldDir.getName());
                    sendClientMessageAsync(
                            Text.literal("No changes to backup for " + worldDir.getName()).formatted(Formatting.GRAY));
                    // git.close(); // Closed in finally
                    return;
                }
                String commitMessage = "Automated backup: "
                        + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                GithubBackupMod.LOGGER.info("Committing changes with message: \"{}\"", commitMessage);
                try {
                    git.commit().setMessage(commitMessage).setAuthor("Minecraft Backup Mod", "backup@mod.local").call();
                } catch (EmptyCommitException e) {
                    GithubBackupMod.LOGGER.info("No changes to commit for {}.", worldDir.getName());
                    sendClientMessageAsync(
                            Text.literal("No changes to backup for " + worldDir.getName()).formatted(Formatting.GRAY));
                    // git.close(); // Closed in finally
                    return;
                }
                GithubBackupMod.LOGGER.info("Pushing changes to origin/main for {}", repoFullName);
                PushCommand pushCommand = git.push();
                pushCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(accessToken, ""));
                pushCommand.setRemote("origin").add("refs/heads/main:refs/heads/main");
                pushCommand.call();
                GithubBackupMod.LOGGER.info("Successfully backed up {} to {}", worldDir.getName(), repoFullName);
                sendClientMessageAsync(
                        Text.literal("Backup complete for " + worldDir.getName()).formatted(Formatting.GREEN));
                // --- Ensure folder name matches canonical casing ---
                String canonicalName = worldDir.getCanonicalFile().getName();
                if (!worldDir.getName().equals(canonicalName)) {
                    File correctDir = new File(worldDir.getParentFile(), canonicalName);
                    if (!correctDir.exists()) {
                        boolean renamed = worldDir.renameTo(correctDir);
                        if (renamed) {
                            GithubBackupMod.LOGGER.info("Renamed world folder to canonical casing: {}", canonicalName);
                        } else {
                            GithubBackupMod.LOGGER.warn("Failed to rename world folder to canonical casing: {}",
                                    canonicalName);
                        }
                    }
                }
            } catch (TransportException e) {
                GithubBackupMod.LOGGER.error("Git transport error for {}: {}", worldDir.getName(), e.getMessage());
                String specificError = e.getMessage();
                if (specificError.contains("not found")) {
                    sendClientMessageAsync(Text.literal(
                            "Backup FAILED for " + worldDir.getName() + ": Repository not found or access denied.")
                            .formatted(Formatting.RED));
                } else if (specificError.contains("Authentication not supported")) {
                    sendClientMessageAsync(Text.literal(
                            "Backup FAILED for " + worldDir.getName() + ": GitHub token likely invalid or expired.")
                            .formatted(Formatting.RED));
                } else {
                    sendClientMessageAsync(
                            Text.literal("Backup FAILED for " + worldDir.getName() + ". Transport error. Check logs.")
                                    .formatted(Formatting.RED));
                }
            } catch (URISyntaxException e) { // Catch URISyntaxException
                GithubBackupMod.LOGGER.error("Git operation failed for {}: Invalid remote URL syntax '{}': {}",
                        worldDir.getName(), remoteUrl, e.getMessage(), e);
                sendClientMessageAsync(
                        Text.literal("Backup FAILED for " + worldDir.getName() + ". Invalid Git URL. Check logs.")
                                .formatted(Formatting.RED));
            } catch (GitAPIException | IOException e) { // Catch other Git and IO exceptions
                GithubBackupMod.LOGGER.error("Git operation failed for {}: {}", worldDir.getName(), e.getMessage(), e);
                sendClientMessageAsync(Text
                        .literal(
                                "Backup FAILED for " + worldDir.getName() + ". Error: " + e.getMessage().split("\n")[0])
                        .formatted(Formatting.RED));
            } finally {
                if (git != null) {
                    git.close();
                }
            }
        }, CompletableFuture.delayedExecutor(100, java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    public CompletableFuture<Void> pullWorld(File worldDir, String repoFullName) {
        return CompletableFuture.runAsync(() -> {
            if (!isConfigured() || repoFullName == null || repoFullName.isEmpty()) {
                sendClientMessageAsync(
                        Text.literal("Pull skipped for " + worldDir.getName() + ": Not configured or no repo name.")
                                .formatted(Formatting.YELLOW));
                return;
            }
            sendClientMessageAsync(Text.literal("Checking remote for " + worldDir.getName() + " from " + repoFullName)
                    .formatted(Formatting.AQUA));
            File gitDir = new File(worldDir, ".git");
            String remoteUrl = "https://github.com/" + repoFullName + ".git";
            Git git = null;
            boolean retryAfterCreate = false;
            try {
                if (!gitDir.exists() || !new File(gitDir, "config").exists()) {
                    GithubBackupMod.LOGGER.info(
                            "Local world {} not a git repo or .git corrupted. Attempting to clone from {}",
                            worldDir.getName(), remoteUrl);
                    if (worldDir.exists() && worldDir.listFiles() != null && worldDir.listFiles().length > 0
                            && !gitDir.exists()) {
                        sendClientMessageAsync(Text.literal(worldDir.getName()
                                + " exists locally but is not a Git repo. Please move/backup existing files if you want to clone.")
                                .formatted(Formatting.YELLOW));
                        GithubBackupMod.LOGGER.warn(
                                "Skipping clone for {} as non-git files exist. User must manually resolve.",
                                worldDir.getName());
                        return;
                    }
                    if (gitDir.exists()) {
                        deleteDirectory(gitDir);
                    }
                    if (worldDir.exists() && worldDir.listFiles() != null && worldDir.listFiles().length > 0) {
                        sendClientMessageAsync(Text
                                .literal("Cannot clone " + worldDir.getName()
                                        + ": Directory not empty after cleaning .git. Please backup/remove files.")
                                .formatted(Formatting.RED));
                        return;
                    }
                    try {
                        git = Git.cloneRepository()
                                .setURI(remoteUrl)
                                .setDirectory(worldDir)
                                .setBranch("main")
                                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(accessToken, ""))
                                .call();
                        GithubBackupMod.LOGGER.info("Successfully cloned {} into {}", repoFullName, worldDir.getName());
                        sendClientMessageAsync(
                                Text.literal("Cloned " + worldDir.getName() + " from GitHub.")
                                        .formatted(Formatting.GREEN));
                    } catch (GitAPIException e) {
                        Throwable cause = e.getCause();
                        if (cause instanceof org.eclipse.jgit.errors.NoRemoteRepositoryException) {
                            // Repo does not exist, create it and retry clone once
                            GithubBackupMod.LOGGER.warn("Repo {} does not exist, creating and retrying clone...",
                                    repoFullName);
                            String createdRepo = null;
                            try {
                                createdRepo = createPrivateRepoIfNotExists(worldDir.getName()).get();
                            } catch (InterruptedException | java.util.concurrent.ExecutionException ex) {
                                GithubBackupMod.LOGGER.error("Failed to create repo for {}: {}", worldDir.getName(),
                                        ex.getMessage());
                            }
                            if (createdRepo != null && !createdRepo.isEmpty()) {
                                // Update config if possible
                                try {
                                    com.tomesh.worldsyncer.config.ModConfig config = com.tomesh.worldsyncer.GithubBackupMod
                                            .getConfig();
                                    com.tomesh.worldsyncer.config.ModConfig.WorldBackupEntry entry = config.backedUpWorlds
                                            .get(com.tomesh.worldsyncer.config.ModConfig.worldKey(worldDir.getName()));
                                    if (entry != null) {
                                        entry.repoFullName = createdRepo;
                                        me.shedaniel.autoconfig.AutoConfig
                                                .getConfigHolder(com.tomesh.worldsyncer.config.ModConfig.class).save();
                                    }
                                } catch (Exception ignored) {
                                }
                                // Retry clone with new repo name
                                String retryUrl = "https://github.com/" + createdRepo + ".git";
                                try {
                                    git = Git.cloneRepository()
                                            .setURI(retryUrl)
                                            .setDirectory(worldDir)
                                            .setBranch("main")
                                            .setCredentialsProvider(
                                                    new UsernamePasswordCredentialsProvider(accessToken, ""))
                                            .call();
                                    GithubBackupMod.LOGGER.info("Successfully cloned {} into {} after repo creation",
                                            createdRepo, worldDir.getName());
                                    sendClientMessageAsync(
                                            Text.literal("Cloned " + worldDir.getName()
                                                    + " from GitHub after repo creation.").formatted(Formatting.GREEN));
                                } catch (Exception ex) {
                                    GithubBackupMod.LOGGER.error("Failed to clone after repo creation for {}: {}",
                                            worldDir.getName(), ex.getMessage());
                                    sendClientMessageAsync(Text
                                            .literal("Failed to clone after repo creation for " + worldDir.getName())
                                            .formatted(Formatting.RED));
                                }
                            } else {
                                GithubBackupMod.LOGGER.error("Failed to create repo {} for world {}", repoFullName,
                                        worldDir.getName());
                                sendClientMessageAsync(Text.literal("Failed to create repo for " + worldDir.getName())
                                        .formatted(Formatting.RED));
                            }
                            return;
                        } else {
                            throw e;
                        }
                    }
                } else {
                    git = Git.open(worldDir);
                    GithubBackupMod.LOGGER.info("Pulling changes for {}", worldDir.getName());
                    var remotes = git.remoteList().call();
                    boolean originExists = remotes.stream().anyMatch(r -> r.getName().equals("origin"));
                    if (!originExists) {
                        git.remoteAdd().setName("origin").setUri(new URIish(remoteUrl)).call();
                    } else if (remotes.stream().anyMatch(
                            r -> r.getName().equals("origin") && !r.getURIs().get(0).toString().equals(remoteUrl))) {
                        git.remoteSetUrl().setRemoteName("origin").setRemoteUri(new URIish(remoteUrl)).call();
                    }
                    FetchResult fetchResult = git.fetch()
                            .setRemote("origin")
                            .setCredentialsProvider(new UsernamePasswordCredentialsProvider(accessToken, ""))
                            .call();
                    Ref remoteMain = fetchResult.getAdvertisedRef("refs/heads/main");
                    ObjectId localMain = git.getRepository().resolve("refs/heads/main");
                    if (remoteMain == null) {
                        GithubBackupMod.LOGGER.warn("Remote branch 'main' not found for {}. Nothing to pull.",
                                worldDir.getName());
                        sendClientMessageAsync(Text.literal("Remote 'main' branch not found for " + worldDir.getName())
                                .formatted(Formatting.YELLOW));
                        return;
                    }
                    // Read .worldsyncer.json to get real world name
                    File meta = new File(worldDir, ".worldsyncer.json");
                    String realWorldName = worldDir.getName();
                    if (meta.exists()) {
                        try (java.io.FileReader reader = new java.io.FileReader(meta)) {
                            char[] buf = new char[256];
                            int len = reader.read(buf);
                            String json = new String(buf, 0, len);
                            int idx = json.indexOf(":");
                            if (idx != -1) {
                                String val = json.substring(idx + 1).replaceAll("[\"{}]", "").trim();
                                if (!val.isEmpty())
                                    realWorldName = val;
                            }
                        } catch (Exception e) {
                            GithubBackupMod.LOGGER.warn("Could not read .worldsyncer.json: {}", e.getMessage());
                        }
                    }
                    // --- Ensure folder name matches canonical casing ---
                    File currentWorldDir = worldDir;
                    if (!currentWorldDir.getName().equals(realWorldName)) {
                        File correctDir = new File(currentWorldDir.getParentFile(), realWorldName);
                        if (!correctDir.exists()) {
                            boolean renamed = currentWorldDir.renameTo(correctDir);
                            if (renamed) {
                                GithubBackupMod.LOGGER.info("Renamed world folder to canonical casing: {}",
                                        realWorldName);
                                currentWorldDir = correctDir;
                            } else {
                                GithubBackupMod.LOGGER.warn("Failed to rename world folder to canonical casing: {}",
                                        realWorldName);
                            }
                        }
                    }
                    // --- FORCE OVERWRITE LOCAL WITH REMOTE ---
                    try {
                        git.reset().setMode(org.eclipse.jgit.api.ResetCommand.ResetType.HARD)
                                .setRef("origin/main").call();
                        git.clean().setCleanDirectories(true).setForce(true).call();
                        GithubBackupMod.LOGGER.info("Local world '{}' was forcefully overwritten with remote version.",
                                currentWorldDir.getName());
                        sendClientMessageAsync(Text
                                .literal("Local world '" + currentWorldDir.getName()
                                        + "' was overwritten with the latest version from GitHub.")
                                .formatted(Formatting.YELLOW));
                    } catch (Exception ex) {
                        GithubBackupMod.LOGGER.error("Failed to hard reset/clean for {}: {}", currentWorldDir.getName(),
                                ex.getMessage());
                        sendClientMessageAsync(
                                Text.literal("Failed to force overwrite local world '" + currentWorldDir.getName()
                                        + "'. Manual intervention may be required.").formatted(Formatting.RED));
                        return;
                    }
                    PullResult pullResult = git.pull()
                            .setRemote("origin").setRemoteBranchName("main")
                            .setCredentialsProvider(new UsernamePasswordCredentialsProvider(accessToken, ""))
                            .call();
                    if (pullResult.isSuccessful()) {
                        GithubBackupMod.LOGGER.info("Successfully pulled latest changes for {}",
                                currentWorldDir.getName());
                        sendClientMessageAsync(
                                Text.literal("Pulled latest for " + currentWorldDir.getName())
                                        .formatted(Formatting.GREEN));
                    } else {
                        GithubBackupMod.LOGGER.warn("Pull for {} was not strictly successful, may have conflicts: {}",
                                currentWorldDir.getName(),
                                pullResult.getMergeResult() != null ? pullResult.getMergeResult().getMergeStatus()
                                        : "Unknown status");
                        sendClientMessageAsync(Text
                                .literal("Pull for " + currentWorldDir.getName()
                                        + " completed. Check for conflicts if any.")
                                .formatted(Formatting.YELLOW));
                    }
                }
            } catch (TransportException e) {
                GithubBackupMod.LOGGER.error("Git transport error during pull/clone for {}: {}", worldDir.getName(),
                        e.getMessage());
                String specificError = e.getMessage();
                if (specificError.contains("not found")) {
                    sendClientMessageAsync(Text.literal(
                            "Pull FAILED for " + worldDir.getName() + ": Repository not found or access denied.")
                            .formatted(Formatting.RED));
                } else if (specificError.contains("Authentication not supported")) {
                    sendClientMessageAsync(Text.literal(
                            "Pull FAILED for " + worldDir.getName() + ": GitHub token likely invalid or expired.")
                            .formatted(Formatting.RED));
                } else {
                    sendClientMessageAsync(
                            Text.literal("Pull FAILED for " + worldDir.getName() + ". Transport error. Check logs.")
                                    .formatted(Formatting.RED));
                }
            } catch (URISyntaxException e) { // Catch URISyntaxException
                GithubBackupMod.LOGGER.error("Git operation failed for {}: Invalid remote URL syntax '{}': {}",
                        worldDir.getName(), remoteUrl, e.getMessage(), e);
                sendClientMessageAsync(
                        Text.literal("Pull/Clone FAILED for " + worldDir.getName() + ". Invalid Git URL. Check logs.")
                                .formatted(Formatting.RED));
            } catch (GitAPIException | IOException e) { // Catch other Git and IO exceptions
                GithubBackupMod.LOGGER.error("Git pull/clone failed for {}: {}", worldDir.getName(), e.getMessage(), e);
                sendClientMessageAsync(Text.literal(
                        "Pull/Clone FAILED for " + worldDir.getName() + ". Error: " + e.getMessage().split("\n")[0])
                        .formatted(Formatting.RED));
            } finally {
                if (git != null) {
                    git.close();
                }
            }
        }, CompletableFuture.delayedExecutor(100, java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    public static Path getSavesDir() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            GithubBackupMod.LOGGER
                    .error("MinecraftClient.getInstance() returned null in getSavesDir(). This is unexpected.");
            return Path.of("saves");
        }
        return client.getLevelStorage().getSavesDirectory();
    }

    private boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }

    // Lists all repos for the authenticated user that match the Minecraft world
    // backup pattern
    public CompletableFuture<Map<String, String>> listMinecraftWorldRepos() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, String> result = new HashMap<>();
            if (!isConfigured()) {
                sendClientMessageAsync(
                        Text.literal("GitHub service not configured. Cannot list repos.").formatted(Formatting.RED));
                return result;
            }
            try {
                String ownerLogin = github.getMyself().getLogin();
                for (GHRepository repo : github.getMyself().listRepositories()) {
                    String repoName = repo.getName();
                    if (repoName.startsWith("minecraft-world-")) {
                        // Extract world folder name from repo name (replace dashes with spaces)
                        String worldName = repoName.substring("minecraft-world-".length()).replace('-', ' ');
                        result.put(worldName, ownerLogin + "/" + repoName);
                    }
                }
            } catch (IOException e) {
                GithubBackupMod.LOGGER.error("Failed to list GitHub repositories: {}", e.getMessage(), e);
                sendClientMessageAsync(
                        Text.literal("Failed to list GitHub repos: " + e.getMessage()).formatted(Formatting.RED));
            }
            return result;
        }, CompletableFuture.delayedExecutor(100, java.util.concurrent.TimeUnit.MILLISECONDS));
    }
}