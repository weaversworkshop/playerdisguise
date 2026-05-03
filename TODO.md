# PlayerDisguise — TODO

Scope reminder: iterate on NeoForge + common only. `Core/fabric` is parked.

## Client config & title-screen UI
- Add a client-only config storing `pseudonymName` and `pseudonymSkin` (PNG bytes + SHA-256 hash).
  - Persist to the mod's config dir.
  - Validate skin against Minecraft skin specs (64x64 or 64x32 PNG, ≤64 KB) on load and on edit.
- Add a title-screen button/screen for editing pseudonym name and selecting a skin file.
  - File picker for PNG.
  - Show validation errors inline.
  - Disallow editing while connected to a server (changes only take effect on next login).

## Local (singleplayer / self-view) disguise
- Apply the configured pseudonym + skin to the local player's own rendering at all times outside of multiplayer.
  - Self-view in inventory, F5, etc.
  - Tab list and F3 overlay reflect the pseudonym locally.
- This layer is purely cosmetic; the wire-level identity sent to servers is unchanged.

## Network protocol (login handshake)
- Define custom packet channel for the mod.
- Packet: `S→C ServerKnownSkin { skinHash: Optional<SHA256> }`
  - Sent by server immediately after player login.
  - `None` means server has no cached skin for this player.
- Packet: `C→S ClientDisguiseChoice`
  - Variant `UseReal` — no disguise this session.
  - Variant `UsePseudonym { name, skinHash, skinBlob: Optional<bytes> }`
    - Include `skinBlob` only if `skinHash` differs from what the server reported.
- Packet: `S→C PlayerDisguiseUpdate { uuid, pseudonym, skinHash }`
  - Broadcast on any player joining with a disguise.
  - Sent in bulk to a joining player for all already-online disguised players.
- Packet: `C→S RequestSkinBlob { hash }` — when client lacks a hash locally.
- Packet: `S→C SkinBlob { hash, bytes }` — server response to the request.
- Disguise is locked for the session; changing pseudonym or skin requires logout + login.

## Validation
- Client validates own skin before sending (PNG, 64x64 or 64x32, ≤64 KB).
- Server validates received skin blob:
  - PNG decode succeeds.
  - Dimensions are 64x64 or 64x32.
  - Size ≤64 KB.
  - SHA-256 of bytes matches the claimed `skinHash`.
- On server-side rejection: log warning, treat the player as `UseReal` for the session, notify the client.

## Server-side storage
- Global blob store: `skinHash → blob bytes`.
- Per-player pointer: `playerUUID → (pseudonymName, skinHash)`.
- Persist both alongside the world save (e.g. `<world>/playerdisguise/`).
  - Survives server restarts.
- Load on server start, flush on shutdown (and periodically).
- Garbage-collect orphan blobs (no player pointer references them) on shutdown or via timer.

## Client-side blob cache
- Cache skin blobs by hash in the mod's config dir.
- Look up locally before requesting from the server.
- Optional cap / LRU eviction to prevent unbounded growth.

## Disguise application (the "override everything" layer)
- Maintain a client-side disguise registry: `playerUUID → (pseudonym, skinResourceLocation)`.
- On `PlayerDisguiseUpdate` receipt:
  - If skin hash unknown locally, request blob, then register the blob bytes as a dynamic texture under a deterministic `ResourceLocation`.
  - Mutate the cached `GameProfile` for that player (name + `textures` property) so anything reading the profile directly sees the disguise.
- Mixins (single choke points so other mods inherit the disguise for free):
  - `PlayerInfo#getSkin` — return the disguise's `PlayerSkin` when one is registered for that UUID.
  - `Player#getName` — return the pseudonym `Component` when disguised.
  - `Player#getDisplayName` — same.
- Apply the same disguise pipeline to the local player so the user sees themselves disguised on the server too.

## Server-side name rewriting
- Rewrite chat packet sender names to the pseudonym before broadcast to other clients.
- Decide and document: leave server console / logs as real names (admin visibility).
- Audit other server-side player-name surfaces (death messages, join/leave, `/msg` routing) and either rewrite or document the gap.
  - `/msg <pseudonym>` routing: map pseudonyms → real UUID server-side so private messages still deliver.

## Out of scope (intentional)
- No external skin host / outbound HTTP. Skins flow only between the modded client and modded server.
- Vanilla servers see the player's real identity — by design.
- Vanilla clients on a modded server see real identities for everyone — by design.

## Documentation
- README: clearly disclose that the mod transmits user-supplied skin PNGs and pseudonyms to servers running this mod (Modrinth rule 1.11 — disclosure of data uploads).
- In-game first-run notice covering the same.
