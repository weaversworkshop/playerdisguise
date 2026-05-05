# Player Disguise

[![Minecraft Version](https://img.shields.io/badge/Minecraft-v1.21.1-blue?style=flat-square)](https://www.minecraft.net/en-us)
[![NeoForge Version](https://img.shields.io/badge/NeoForge-v21.1.219-orange?style=flat-square)](https://neoforged.net/)
[![License](https://img.shields.io/badge/License-MPL_2.0-brightgreen?style=flat-square)](./LICENSE)

---

> ⚠️ **Development branch.** This is the active development branch for Minecraft 1.21.1. Code here may be unstable, untested, or mid-refactor.

# Project Overview

Player Disguise lets players take on the name and skin of another player, with server-authoritative
identity tracking so admins can always see who is really behind a disguise. Disguises persist across
logout, claimed aliases are reserved per-player, and a cooldown protects recently-released names from
being snatched.

The mod ships with a set of admin tools (`/whois`, `/namehistory`) that surface the full alias history
for moderation, and integrates with popular client/server mods so disguises render correctly in chat,
on maps, and in voice groups.

Targeting Minecraft 1.21.1 on NeoForge. A Fabric port is planned.

If you encounter a problem, please [submit a bug report!](https://github.com/weaversworkshop/playerdisguise/issues)


## 📜 Credits

- **Mod Author** - weaversworkshop


## Features

- **Player disguises** — make profiles with custom skins and player names to swap between characters
- **Persistent identities** — aliases stay claimed on servers you join until you switch names
- **Alias reservation & cooldown** — three-tier list prevents impersonation and name-grabbing
- **Admin lookup tools** — `/whois` (name-anchored) and `/namehistory` (player-anchored) for moderation
- **Client/server storage hygiene** — unused skins are efficiently cleaned up on both client and server instances
- **Compat integrations** — Simple Voice Chat, BlueMap, JourneyMap, Chat Heads, and more

## Compatibility

| Mod | Purpose |
| --- | --- |
| [Simple Voice Chat](https://modrinth.com/mod/simple-voice-chat) | Voice icons follow the disguised identity |
| [BlueMap](https://modrinth.com/mod/bluemap) | Map markers reflect the disguise |
| [JourneyMap](https://modrinth.com/mod/journeymap) | Map markers reflect the disguise |
| [Chat Heads](https://modrinth.com/mod/chat-heads) | Chat avatars match the disguised skin |


---

# Contributing

PRs and issues are welcome!

**Branching workflow.** This project uses a three-tier hierarchy:

```
1.21.1-release  ←  1.21.1-dev  ←  feature/bugfix branches
```

- **`1.21.1-release`** — stable, public-facing. Only stabilization merges from `1.21.1-dev` land here.
- **`1.21.1-dev`** — active integration branch. PRs target this branch.
- **Feature / bugfix branches** — your work. Always branch off `1.21.1-dev` and open one branch per feature or fix. Use a descriptive name (e.g. `feat/my-feature`, `bug/fix-skin-desync`). Do **not** commit directly to `1.21.1-dev` or `1.21.1-release`.

## 📦 Building/Running the project

Build the NeoForge mod with:

- `gradlew clean :Core:neoforge:build`
  - Output jar lands in `Core/neoforge/build/libs/`.

Dev runs are pre-configured for the NeoForge subproject. After importing the project, the following Gradle tasks are available:

- `:Core:neoforge:runClient` — single-player dev client (username `Dev`)
- `:Core:neoforge:runClient2` — second dev client (username `Dev2`) for two-player local testing
- `:Core:neoforge:runServer` — dedicated dev server

Run directories live under `Core/neoforge/run/{client,client2,server}`. Dev-only compat mods (e.g. Chat Heads) in `Core/neoforge/libs/` are auto-copied into each run's `mods/` folder before launch.

> **Note:** `Core/fabric` is not currently implemented. The Fabric port is not built or tested as part of the current development cycle.

## 📈 Updating Versions / Adding Dependencies

Mod and platform versions are centralized in `gradle.properties` files to keep updates simple:

- Project-wide values: `gradle.properties` (root)
- NeoForge-specific values: `Core/neoforge/gradle.properties` (Minecraft, NeoForge, Parchment, mod id/version)

### For Minecraft / NeoForge Version Updates:

- Update `minecraft_version`, `minecraft_version_support_range`, and `neoforge_version` in `Core/neoforge/gradle.properties`.
- Update `parchment_minecraft_version` and `parchment_version` to a matching Parchment release.
- Update the badges in this README to reflect the new versions.

### For New Compat Modules:

- Add a new `[[mixins]]` entry in `Core/neoforge/src/main/resources/META-INF/neoforge.mods.toml`.
- Drop any dev-only jars into `Core/neoforge/libs/` and wire them into the `devRuntimeMods` configuration in `Core/neoforge/build.gradle`.

---
