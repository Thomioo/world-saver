package com.tomesh.worldsaver.config;

import com.tomesh.worldsaver.ClientInit;
import com.tomesh.worldsaver.GithubBackupMod;
import com.tomesh.worldsaver.config.ModConfig.WorldBackupEntry;
import com.tomesh.worldsaver.core.GithubService;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import net.minecraft.util.Identifier;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

public class WorldBackupConfigScreen extends Screen {
    private final Screen parent;
    private final List<String> worldNames = new ArrayList<>();
    private final Map<String, WorldBackupEntry> backupEntries = new HashMap<>();
    private final List<TextFieldWidget> repoFields = new ArrayList<>();
    private ButtonWidget saveButton;
    private final Map<String, Boolean> backupEnabled = new HashMap<>();
    private final List<ButtonWidget> toggleButtons = new ArrayList<>();
    private TextFieldWidget tokenField;
    private ButtonWidget showHideTokenButton;
    private boolean tokenVisible = false;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private static final int ENTRY_HEIGHT = 28;
    private static final int BOX_PADDING = 0;
    private static final int BOX_RADIUS = 0;
    private static final int HEADER_HEIGHT = 32;
    private static final int TOKEN_SECTION_HEIGHT = 28;
    private static final int SECTION_SPACING = 12;
    private static final int SIDE_PADDING = 20;
    private static final int WORLD_SECTION_TOP = HEADER_HEIGHT + TOKEN_SECTION_HEIGHT + SECTION_SPACING;
    private static final int WORLD_LIST_TOP_PADDING = 20;
    private static final int WORLD_LIST_BOTTOM_PADDING = 16;
    private static final int WORLD_NAME_LEFT_PADDING = 16;
    private static final int TOGGLE_REPO_SPACING = 16;
    private static final int BOTTOM_PADDING = 20;
    private int visibleEntries = 8;
    private String realToken = "";

    public WorldBackupConfigScreen(Screen parent) {
        super(Text.literal("World Saver Backup Config"));
        this.parent = parent;
        // Clean up config of worlds not present locally or on GitHub
        com.tomesh.worldsaver.ClientInit.cleanConfigOfNonexistentWorlds();
        // Fetch all possible worlds from GitHub and local saves
        Map<String, String> lowerToRealName = new HashMap<>();
        // Add all local worlds (real folder names)
        File savesDir = new File(MinecraftClient.getInstance().runDirectory, "saves");
        if (savesDir.exists() && savesDir.isDirectory()) {
            File[] worldDirs = Objects.requireNonNull(savesDir.listFiles(File::isDirectory));
            for (File f : worldDirs) {
                String realName = f.getName();
                lowerToRealName.put(realName, realName);
            }
        }
        // Add all config worlds (only if not already present as a real folder)
        ModConfig config = me.shedaniel.autoconfig.AutoConfig.getConfigHolder(ModConfig.class).getConfig();
        for (String configKey : config.backedUpWorlds.keySet()) {
            if (!lowerToRealName.containsKey(configKey)) {
                lowerToRealName.put(configKey, configKey); // fallback to config key
            }
        }
        // Add all remote worlds (only if not already present)
        try {
            Map<String, String> remoteWorlds = ClientInit.getGithubService().listMinecraftWorldRepos().get();
            for (String remoteKey : remoteWorlds.keySet()) {
                if (!lowerToRealName.containsKey(remoteKey)) {
                    lowerToRealName.put(remoteKey, remoteKey);
                }
            }
        } catch (Exception e) {
            GithubBackupMod.LOGGER.error("Failed to fetch world repos for config UI: {}", e.getMessage());
        }
        worldNames.addAll(new HashSet<>(lowerToRealName.values()));
        Collections.sort(worldNames);
        // Load current config
        for (String world : worldNames) {
            String key = com.tomesh.worldsaver.config.ModConfig.worldKey(world);
            WorldBackupEntry entry = config.backedUpWorlds.getOrDefault(key, new WorldBackupEntry("", false));
            backupEntries.put(world, entry);
            backupEnabled.put(world, entry.enabled);
        }
    }

    @Override
    protected void init() {
        // Remove old toggle buttons before clearing and adding new ones
        for (ButtonWidget btn : toggleButtons) {
            this.remove(btn);
        }
        this.toggleButtons.clear();
        this.repoFields.clear();
        // Calculate the vertical space above the world list
        int tokenSectionHeight = TOKEN_SECTION_HEIGHT;
        int worldListStartY = HEADER_HEIGHT + TOKEN_SECTION_HEIGHT + SECTION_SPACING + WORLD_LIST_TOP_PADDING;
        int buttonHeight = 20;
        int saveBtnY = this.height - BOTTOM_PADDING - buttonHeight;
        int worldListY = WORLD_SECTION_TOP;
        int entryBoxWidth = this.width - 2 * SIDE_PADDING;
        int nameWidth = 180; // fixed width for world name label
        int toggleWidth = 120; // fixed width for toggle button
        int pairSpacing = 16;
        int pairWidth = nameWidth + pairSpacing + toggleWidth;
        int pairStartX = SIDE_PADDING + (entryBoxWidth - pairWidth) / 2;
        // Dynamically calculate how many entries fit
        int availableListHeight = saveBtnY - worldListY - WORLD_LIST_BOTTOM_PADDING;
        if (availableListHeight < ENTRY_HEIGHT) {
            // Not enough space for even one entry, add padding below token section
            worldListY += (ENTRY_HEIGHT - availableListHeight);
            availableListHeight = ENTRY_HEIGHT;
        }
        this.visibleEntries = Math.max(1, availableListHeight / ENTRY_HEIGHT);
        int startIdx = scrollOffset;
        int endIdx = Math.min(worldNames.size(), startIdx + visibleEntries);
        // Access token field (make it wider, up to 800px or as much as the screen
        // allows)
        String configToken = me.shedaniel.autoconfig.AutoConfig.getConfigHolder(ModConfig.class)
                .getConfig().githubAccessToken;
        if (realToken.isEmpty())
            realToken = configToken;
        int tokenFieldWidth = Math.min(this.width - 2 * SIDE_PADDING - 60, 800);
        int tokenStartY = HEADER_HEIGHT + SECTION_SPACING;
        this.tokenField = new TextFieldWidget(this.textRenderer, this.width / 2 - tokenFieldWidth / 2, tokenStartY,
                tokenFieldWidth, 20,
                Text.literal("GitHub Access Token"));
        if (tokenVisible) {
            this.tokenField.setText(realToken);
        } else {
            this.tokenField.setText("*".repeat(realToken.length()));
        }
        this.tokenField.setEditable(tokenVisible || realToken.isEmpty());
        this.tokenField.setEditableColor(0xFFFFFF);
        this.tokenField.setMaxLength(128);
        // Listen for changes only if visible
        this.tokenField.setChangedListener(text -> {
            if (tokenVisible) {
                realToken = text;
            }
        });
        if (tokenVisible) {
            this.tokenField.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(realToken)));
        } else {
            this.tokenField.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text
                    .literal(
                            "Paste your GitHub Personal Access Token here. It must have 'repo' scope. Keep it private!")));
        }
        this.addDrawableChild(this.tokenField);
        this.showHideTokenButton = ButtonWidget.builder(Text.literal(tokenVisible ? "Hide" : "Show"), btn -> {
            if (realToken.isEmpty() && !tokenVisible) {
                // If token is empty, do not allow hiding (keep visible for pasting)
                return;
            }
            tokenVisible = !tokenVisible;
            btn.setMessage(Text.literal(tokenVisible ? "Hide" : "Show"));
            if (tokenVisible) {
                this.tokenField.setText(realToken);
                this.tokenField.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(realToken)));
            } else {
                this.tokenField.setText("*".repeat(realToken.length()));
                this.tokenField.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text
                        .literal(
                                "Paste your GitHub Personal Access Token here. It must have 'repo' scope. Keep it private!")));
            }
            this.tokenField.setEditable(tokenVisible || realToken.isEmpty());
            this.tokenField.setCursorToEnd();
        }).dimensions(this.width / 2 + tokenFieldWidth / 2 + 6, tokenStartY, 40, 20).build();
        this.addDrawableChild(this.showHideTokenButton);
        // --- World List ---
        for (int i = startIdx; i < endIdx; i++) {
            String world = worldNames.get(i);
            final int index = i;
            boolean enabled = backupEnabled.getOrDefault(world, false);
            WorldBackupEntry entry = backupEntries.get(world);
            int boxY = WORLD_SECTION_TOP + WORLD_LIST_TOP_PADDING + (i - startIdx) * ENTRY_HEIGHT;
            int nameX = pairStartX;
            int toggleX = nameX + nameWidth + pairSpacing;
            ButtonWidget toggle = ButtonWidget.builder(
                    Text.literal(enabled ? "Backup: ON" : "Backup: OFF"),
                    btn -> {
                        boolean newState = !backupEnabled.getOrDefault(world, false);
                        backupEnabled.put(world, newState);
                        btn.setMessage(Text.literal(newState ? "Backup: ON" : "Backup: OFF"));
                        if (newState) {
                            if (entry.repoFullName == null || entry.repoFullName.isEmpty()) {
                                try {
                                    String createdRepo = ClientInit.getGithubService()
                                            .createPrivateRepoIfNotExists(world).get();
                                    if (createdRepo != null && !createdRepo.isEmpty()) {
                                        entry.repoFullName = createdRepo;
                                    }
                                } catch (Exception e) {
                                    GithubBackupMod.LOGGER.error("Failed to create repo for world {}: {}", world,
                                            e.getMessage());
                                }
                            }
                            File savesDir = new File(MinecraftClient.getInstance().runDirectory, "saves");
                            File worldDir = new File(savesDir, world);
                            if (!worldDir.exists() && entry.repoFullName != null && !entry.repoFullName.isEmpty()) {
                                try {
                                    ClientInit.getGithubService().pullWorld(worldDir, entry.repoFullName).get();
                                } catch (Exception e) {
                                    GithubBackupMod.LOGGER.error("Failed to clone world {}: {}", world, e.getMessage());
                                }
                            }
                            if (worldDir.exists() && entry.repoFullName != null && !entry.repoFullName.isEmpty()) {
                                File[] files = worldDir.listFiles();
                                boolean hasContent = false;
                                if (files != null) {
                                    for (File file : files) {
                                        String name = file.getName();
                                        if (!name.equals(".git") && !name.equals(".gitignore")) {
                                            hasContent = true;
                                            break;
                                        }
                                    }
                                }
                                if (hasContent) {
                                    try {
                                        ClientInit.getGithubService().backupWorld(worldDir, entry.repoFullName);
                                    } catch (Exception e) {
                                        GithubBackupMod.LOGGER.error("Failed to push world {} to GitHub: {}", world,
                                                e.getMessage());
                                    }
                                } else {
                                    if (MinecraftClient.getInstance().player != null) {
                                        MinecraftClient.getInstance().player.sendMessage(
                                                net.minecraft.text.Text.literal(
                                                        "World folder is empty. Please load and save the world at least once before enabling backup.")
                                                        .formatted(net.minecraft.util.Formatting.YELLOW),
                                                false);
                                    } else {
                                        GithubBackupMod.LOGGER.warn(
                                                "World folder '{}' is empty. Please load and save the world at least once before enabling backup.",
                                                world);
                                    }
                                }
                            }
                        } else {
                            File savesDir = new File(MinecraftClient.getInstance().runDirectory, "saves");
                            File worldDir = new File(savesDir, world);
                            File gitDir = new File(worldDir, ".git");
                            File gitignore = new File(worldDir, ".gitignore");
                            File worldsave = new File(worldDir, ".worldsaver.json");
                            if (gitDir.exists()) {
                                try {
                                    org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.open(gitDir);
                                    git.close();
                                } catch (Exception ignored) {
                                }
                                System.gc();
                                try {
                                    Thread.sleep(200);
                                } catch (InterruptedException ignored) {
                                }
                                deleteDirectoryNio(gitDir.toPath());
                            }
                            if (gitignore.exists())
                                gitignore.delete();
                            if (worldsave.exists())
                                worldsave.delete();
                        }
                    })
                    .dimensions(toggleX, boxY, toggleWidth, 20)
                    .build();
            toggle.setTooltip(net.minecraft.client.gui.tooltip.Tooltip
                    .of(Text.literal("Enable or disable GitHub backup for this world.")));
            this.addDrawableChild(toggle);
            toggleButtons.add(toggle);
        }
        // Scroll buttons if needed
        if (worldNames.size() > visibleEntries) {
            int scrollBtnWidth = 20;
            int scrollBtnX = SIDE_PADDING + entryBoxWidth - scrollBtnWidth - 8; // 8px padding from right edge of bg
            int scrollUpY = WORLD_SECTION_TOP + WORLD_LIST_TOP_PADDING;
            int scrollDownY = scrollUpY + (visibleEntries - 1) * ENTRY_HEIGHT;
            ButtonWidget scrollUp = ButtonWidget.builder(Text.literal("▲"), btn -> {
                if (scrollOffset > 0)
                    scrollOffset--;
                this.init();
            }).dimensions(scrollBtnX, scrollUpY, scrollBtnWidth, 20).build();
            this.addDrawableChild(scrollUp);
            ButtonWidget scrollDown = ButtonWidget.builder(Text.literal("▼"), btn -> {
                if (scrollOffset < worldNames.size() - visibleEntries)
                    scrollOffset++;
                this.init();
            }).dimensions(scrollBtnX, scrollDownY, scrollBtnWidth, 20).build();
            this.addDrawableChild(scrollDown);
        }
        // Place Save & Close button at the bottom with padding
        saveButton = ButtonWidget.builder(Text.literal("Save & Close"), btn -> saveAndClose())
                .dimensions(this.width / 2 - 60, saveBtnY, 120, buttonHeight).build();
        this.addDrawableChild(saveButton);
    }

    private void saveAndClose() {
        // If the token field is currently editable, update realToken with the field's
        // text before saving
        if (this.tokenField != null && (tokenVisible || realToken.isEmpty())) {
            realToken = this.tokenField.getText();
        }
        String prevToken = me.shedaniel.autoconfig.AutoConfig.getConfigHolder(ModConfig.class)
                .getConfig().githubAccessToken;
        String token = realToken;
        ModConfig config = me.shedaniel.autoconfig.AutoConfig.getConfigHolder(ModConfig.class).getConfig();
        config.githubAccessToken = token.trim();
        config.backedUpWorlds.clear();
        List<String> worldsToClean = new ArrayList<>();
        for (int i = 0; i < worldNames.size(); i++) {
            String world = worldNames.get(i);
            String key = com.tomesh.worldsaver.config.ModConfig.worldKey(world);
            WorldBackupEntry entry = backupEntries.get(world);
            boolean wasEnabled = entry.enabled;
            entry.enabled = backupEnabled.getOrDefault(world, false);
            // entry.repoFullName is managed automatically, do not update from UI
            config.backedUpWorlds.put(key, entry);
            // If backup is now disabled, schedule .git/.gitignore cleanup
            if (wasEnabled && !entry.enabled) {
                worldsToClean.add(world);
            }
        }
        me.shedaniel.autoconfig.AutoConfig.getConfigHolder(ModConfig.class).save();
        this.client.setScreen(parent);
        // After UI closes, clean up .git/.gitignore for worlds that were just disabled
        new Thread(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
            }
            File savesDir = new File(MinecraftClient.getInstance().runDirectory, "saves");
            for (String world : worldsToClean) {
                File worldDir = new File(savesDir, world);
                File gitDir = new File(worldDir, ".git");
                File gitignore = new File(worldDir, ".gitignore");
                boolean deleted = false;
                for (int i = 0; i < 5; i++) {
                    deleted = true;
                    if (gitDir.exists()) {
                        try {
                            org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.open(gitDir);
                            git.close();
                        } catch (Exception ignored) {
                        }
                        deleted &= deleteDirectoryNio(gitDir.toPath());
                    }
                    if (gitignore.exists())
                        deleted &= gitignore.delete();
                    if (deleted && (!gitDir.exists()) && (!gitignore.exists()))
                        break;
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ignored) {
                    }
                }
                if (!deleted && (gitDir.exists() || gitignore.exists())) {
                    System.err.println("[WorldSaver] Failed to delete .git/.gitignore for world '" + world
                            + "' after retries. You may need to close Minecraft and delete it manually.");
                }
            }
        }, "WorldSaver-GitCleanup").start();
        // If the token was changed and is now non-empty, re-initialize GithubService
        // and auto-clone worlds
        if (!token.trim().isEmpty() && !token.trim().equals(prevToken)) {
            // Re-initialize GithubService
            com.tomesh.worldsaver.ClientInit.getGithubService(); // This will re-init if needed
            // Run the world auto-config/clone logic
            com.tomesh.worldsaver.ClientInit.runWorldAutoConfigAndClone();
        }
    }

    private static boolean deleteDirectoryNio(java.nio.file.Path dir) {
        try {
            java.nio.file.Files.walkFileTree(dir, new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
                @Override
                public java.nio.file.FileVisitResult visitFile(java.nio.file.Path file,
                        java.nio.file.attribute.BasicFileAttributes attrs) throws java.io.IOException {
                    java.io.File f = file.toFile();
                    if (!f.canWrite())
                        f.setWritable(true);
                    java.nio.file.Files.delete(file);
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult postVisitDirectory(java.nio.file.Path dir, java.io.IOException exc)
                        throws java.io.IOException {
                    java.nio.file.Files.delete(dir);
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
            return true;
        } catch (Exception e) {
            System.err.println("[WorldSaver] NIO delete failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        // Draw mod title (vanilla style)
        int logoY = 8;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, logoY, 0xFFFFFF);
        // Section headers
        int tokenHeaderY = HEADER_HEIGHT - 8;
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("GitHub Access Token"), this.width / 2,
                tokenHeaderY, 0xAAAAAA);
        int worldHeaderY = HEADER_HEIGHT + TOKEN_SECTION_HEIGHT + 20;
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("World Backups"), this.width / 2,
                worldHeaderY, 0xAAAAAA);
        // Draw background for world list (darker rectangle)
        int bgY1 = WORLD_SECTION_TOP;
        int buttonHeight = 20;
        int saveBtnY = this.height - BOTTOM_PADDING - buttonHeight;
        int bgY2 = saveBtnY - SECTION_SPACING;
        int bgX1 = SIDE_PADDING;
        int bgX2 = this.width - SIDE_PADDING;
        int bgWidth = bgX2 - bgX1;
        int bgColor = 0xAA000000; // semi-transparent black
        context.fill(bgX1, bgY1, bgX2, bgY2, bgColor);
        // Centered world name and toggle button
        int y = WORLD_SECTION_TOP + WORLD_LIST_TOP_PADDING;
        int startIdx = scrollOffset;
        int endIdx = Math.min(worldNames.size(), startIdx + visibleEntries);
        int entryBoxWidth = this.width - 2 * SIDE_PADDING;
        int nameWidth = 180; // fixed width for world name label
        int toggleWidth = 120; // fixed width for toggle button
        int pairSpacing = 16;
        int pairWidth = nameWidth + pairSpacing + toggleWidth;
        int pairStartX = SIDE_PADDING + (entryBoxWidth - pairWidth) / 2;
        for (int i = startIdx; i < endIdx; i++) {
            int boxY = y + (i - startIdx) * ENTRY_HEIGHT;
            int nameX = pairStartX;
            context.drawTextWithShadow(this.textRenderer, Text.literal(worldNames.get(i)),
                    nameX, boxY + 6, 0xFFFFFF);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    // (Optional) Add mouse wheel support for scrolling
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (worldNames.size() > visibleEntries) {
            if (amount < 0 && scrollOffset < worldNames.size() - visibleEntries) {
                scrollOffset++;
                this.init();
                return true;
            } else if (amount > 0 && scrollOffset > 0) {
                scrollOffset--;
                this.init();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }
}