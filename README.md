# Player Disguise

[![Player Disguise banner](./docs/media/playerdisguise-banner.gif)](https://github.com/weaversworkshop/playerdisguise)

[![Minecraft Version](https://img.shields.io/badge/Minecraft-v1.21.1-blue?style=flat-square)](https://www.minecraft.net/en-us)
[![NeoForge Version](https://img.shields.io/badge/NeoForge-v21.1.219-orange?style=flat-square)](https://neoforged.net/)
[![License](https://img.shields.io/badge/License-MPL_2.0-brightgreen?style=flat-square)](./LICENSE)

---

# Project Overview

Player Disguise lets players present a custom alias and skin to others on the server for roleplay,
character-driven worlds, or just a fresh identity. Server-authoritative tracking means admins can
always see who is really behind a disguise. Disguises persist across logout, claimed aliases are
reserved per-player, and a cooldown protects recently-released names from being snatched.

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
