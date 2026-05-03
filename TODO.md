# PlayerDisguise — TODO

Scope reminder: iterate on NeoForge + common only. `Core/fabric` is parked.

---

## Done

### Phase 1 — client config + UI + local self-disguise
- Per-profile client config (`pseudonymName`, `skinFileName`, `skinHash`, `skinModel`) persisted to `<configdir>/playerdisguise/client.json`.
- Multi-profile carousel screen accessed from the title screen; profile 0 is the real-identity baseline (un-deletable, fetched via `SkinManager`).
- Profile edit screen with in-game skin picker reading from `<configdir>/playerdisguise/skins/`, slim/wide model toggle, live 3D preview, "Open Folder" button. (No native AWT file dialog.)
- Skin validation: PNG decode, 64×64 or 64×32, ≤64 KB, SHA-256 hashing.
- Missing-skin handling: file deletion mid-session preserves the profile; on next launch the active profile auto-falls-back to profile 0 if its skin is gone; missing skins render a "missing texture" PNG with a tooltip on the Select button.

### Phase 2 — multiplayer protocol + visual disguise + privacy
- Custom packet channel; payloads registered as optional so vanilla clients can still connect.
- Configuration-phase alias handshake: `ServerRequestDisguise` (S→C) → `ClientDisguiseChoice` (C→S, carries pseudonym + skinHash + skinModel + bytes). Rejected claims disconnect the player during config — no PLAY entry, no join broadcast trail.
- `AliasRegistry` (server) — three lists per UUID: claimed alias, cooldown alias (1hr default, configurable), persistent true-name list. Switching aliases evicts prior cooldown. Resume-from-own-cooldown supported.
- Real-name collision policy: if a joining player's real name matches another player's active alias, the alias holder is kicked.
- Per-world persistence to `<world>/playerdisguise/aliases.json` (active + cooldown + true names). Periodic save every 5 min, prune expired every 1 min, flush on shutdown.
- Per-world skin blob store at `<world>/playerdisguise/skins/<hash>.png`. Server validates received blobs (PNG, dims, ≤64KB, hash match) before storing.
- Client skin cache at `<configdir>/playerdisguise/cache/<hash>.png` — checked before requesting from the server.
- Skin blob exchange: `RequestSkinBlob` (C→S) / `SkinBlob` (S→C) for fetching missing hashes during PLAY.
- `PlayerDisguiseUpdate` (S→C) broadcast on join/change; bulk snapshot sent to a joining player so they see existing disguises.
- Visual disguise mixins (client): `PlayerInfo#getSkin`, `PlayerInfo#getTabListDisplayName`, `Player#getName`, `Player#getDisplayName`, `ClientSuggestionProvider#getOnlinePlayerNames`. Disguise applies to the local player too (via the same broadcast path).
- Server-side name rewriting via `Player#getName`/`getDisplayName` mixin → chat sender, death messages, join/leave broadcasts use the alias. Server console / logs intentionally use real names (per design note).
- Command resolution mixins (server): `PlayerList#getPlayerByName` resolves pseudonyms; `PlayerList#getPlayerNamesArray` returns pseudonyms (auto-complete suggests aliases only). `/tp`, `/give`, `/msg`, `/list`, etc. all work with pseudonyms.
- Privacy: `getPlayerByName` returns `null` when the matched player is currently disguised and the lookup string isn't their alias (real names are not addressable while disguised). Exception: lookup commands (see below) must bypass this block.
- Vanilla-client compat: `AliasClaimTask` is only registered when the client has our channel (`hasChannel` check). Vanilla clients pass through config phase untouched and appear normally with their real names.

---

## Remaining

### Storage hygiene
- **Server-side:** garbage-collect orphan skin blobs in `<world>/playerdisguise/skins/` (blobs no longer referenced by any active or cooldown profile). Run on shutdown or on a timer.
- **Client-side:** cap / LRU eviction on `<configdir>/playerdisguise/cache/` so it doesn't grow unbounded.

### Documentation
- README: disclose that the mod transmits user-supplied skin PNGs and pseudonyms to servers running this mod (Modrinth rule 1.11).
- In-game first-run notice covering the same disclosure.

### Lookup commands (`/whois`, `/namehistory`)
Spec is locked in (see `memory/project_lookup_commands.md`). Both commands operate on a name index; build them so they bypass the `getPlayerByName` privacy block (resolve via `AliasRegistry` + a new historical name index, not via `EntityArgument` / vanilla name lookup).

- **`/whois <name>`** — multi-row: every player who has ever held that name (current or past), one row per holder. Each row: real Minecraft name + time interval(s) held (`<start> to <end>` or `<start> to now`).
- **`/namehistory <name>`** — single-target: real Minecraft name at top, then that player's pseudonym history sorted oldest→newest. Each row shows the time interval the alias was held.
- Target argument resolves any string: current alias, prior alias, or real name. First match wins; tie-break: current-alias > most-recent-historical-use.
- **Visibility (op-aware, per-row):**
  - `/namehistory <admin>` when `runner.opLevel < target.opLevel` → lie: show target's current alias as if it were the real name, empty history. No "hidden" indicator (would leak the outrank).
  - `/whois <name>` → silently omit rows where `runner.opLevel < holder.opLevel`. Partial result with no indication.
  - Non-op vs. non-op (`0 >= 0`) → truth.
- Required new state: a persistent **historical name index** mapping each UUID to all aliases they've ever held with `(start, end)` intervals. Live alias entries have `end=null` (open). Update on every claim/release. Persist alongside `aliases.json` under `<world>/playerdisguise/`.

### Quality-of-life (deferred)
- **Singleplayer mid-session profile hot-swap.** TODO is locked-per-session everywhere; SP could allow swapping without re-launching the world.
- **Vanilla-client name visibility consistency.** Today vanilla clients see aliases in chat / death / join-leave (server bakes alias into the Component) but real names in nameplate / skin / tab list / auto-complete (those are client-side). Mixed view is confusing. Either make it fully consistent (always real for vanilla) or block vanilla clients from joining a server with disguises active.
- **Configuration-phase skin upload optimization.** Currently the client always bundles skin bytes with `ClientDisguiseChoice` even if the server already has them. Could re-introduce `ServerKnownSkin` (S→C) sent before the request so the client only uploads when needed.

---

## Out of scope (intentional)
- No external skin host / outbound HTTP. Skins flow only between the modded client and modded server.
- Vanilla servers see the player's real identity — by design.
- Vanilla clients on a modded server see a mixed view (chat = alias, visuals = real) — see deferred QoL item above.
