# 🌍 World Saver - GitHub Backup Mod for Minecraft

![World Saver Banner](https://raw.githubusercontent.com/yourusername/world-saver/main/assets/world-saver/icon.png)

## Overview

**World Saver** is a Minecraft Fabric mod that automatically backs up your worlds to GitHub! Never lose your progress again—sync your worlds to private repositories and restore them anytime, anywhere.

---

## ✨ Features
- 🔄 **Automatic World Backups** to your GitHub account
- 🗂️ **Restore worlds** from GitHub with a single click
- 🔒 **Private repository support** for your worlds
- 🛡️ **Safe deletion** cleans up GitHub data when you delete a world
- 🖥️ **Modern, user-friendly config screen**
- 🧩 **Fabric API** and **Cloth Config** integration

---

## 🚀 Installation

1. **Download the Mod:**
   - [Releases](https://github.com/yourusername/world-saver/releases) (or build from source)
2. **Install [Fabric Loader](https://fabricmc.net/use/installer/)** (version 0.16.14+ recommended)
3. **Install [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api)**
4. **Install [Cloth Config](https://www.curseforge.com/minecraft/mc-mods/cloth-config)**
5. **Place `world-saver-x.x.x.jar` in your `mods/` folder**
6. **Launch Minecraft (1.20.1)**

---

## 📦 Dependencies
- [Fabric Loader](https://fabricmc.net/use/installer/) (>= 0.16.14)
- [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api)
- [Cloth Config](https://www.curseforge.com/minecraft/mc-mods/cloth-config) (>= 11.0.0)
- [ModMenu](https://www.curseforge.com/minecraft/mc-mods/modmenu) (optional, for config screen)
- Java 17+

---

## 🛠️ Usage

1. **Open Minecraft and go to Mods > World Saver > Config**
2. **Paste your GitHub Personal Access Token** (with `repo` scope) in the config screen
   - Click "Show" to reveal the field, paste your token, then click "Hide" to mask it
3. **Enable backup for any world** using the toggle button
4. **Worlds will be backed up to private GitHub repos** automatically
5. **Restore or sync worlds** from GitHub at any time

### 🔑 How to Get a GitHub Token
- Go to [GitHub Settings > Developer settings > Personal access tokens](https://github.com/settings/tokens)
- Click "Generate new token"
- Select `repo` scope
- Copy and paste the token into the mod config

---

## ⚙️ Configuration
- Access the config screen via ModMenu or `/config world-saver` command
- Set your GitHub token, enable/disable backups per world
- All settings are saved in `config/world-saver.json` (managed by Cloth Config)

---

## 🤝 Contributing

Pull requests, issues, and suggestions are welcome!
- Fork the repo
- Create a feature branch
- Submit a pull request

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).

---

## 🙏 Credits
- **Author:** Tomesh
- [FabricMC](https://fabricmc.net/), [Cloth Config](https://github.com/shedaniel/cloth-config), [JGit](https://www.eclipse.org/jgit/), [kohsuke/github-api](https://github.com/hub4j/github-api)
- Special thanks to the Minecraft modding community!

---

## 💬 Support & Feedback

- [GitHub Issues](https://github.com/yourusername/world-saver/issues)
- [Discord](https://discord.gg/yourserver) (if available)

---

> _"Back up your worlds, save your adventures!"_ 