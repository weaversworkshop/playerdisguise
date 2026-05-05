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
- Privacy: `getPlayerByName` returns `null` when the matched player is currently disguised and the lookup string isn't their alias (real names are not addressable while disguised). Exceptions, gated via a `CommandSourceStack` thread-local pushed by a `Commands#performCommand` mixin: server console / command blocks (no entity source) always bypass the block; an op bypasses when `runner.opLevel >= target.opLevel` (and runner is at least op level 1). Lookup commands (`/whois`, `/namehistory`) resolve directly through `AliasRegistry` and don't go through this path.
- Vanilla-client compat: `AliasClaimTask` is only registered when the client has our channel (`hasChannel` check). Vanilla clients pass through config phase untouched and appear normally with their real names.

### Phase 3 — lookup commands + name history + op-aware real-name targeting
- **`/whois <name>`** (name-anchored, multi-row) and **`/namehistory <name>`** (person-anchored, single-target) registered via `RegisterCommandsEvent`. Both resolve directly through `AliasRegistry`, bypassing the `getPlayerByName` privacy block. Per-row op-level visibility: `/whois` silently omits outranked rows; `/namehistory` lies (current alias shown as real name, empty history) when target outranks runner. Non-entity command sources (console / command blocks) treated as op level 4. Roles intentionally complementary: `/whois` answers "who has held this name?", `/namehistory` answers "what names has this player held?".
- **Persistent name history** in `AliasRegistry` via `NameInterval(alias, startMs, endMs, realName)` records, persisted under a new `history` array in `aliases.json` (`endMs == 0` = open). Continuous-timeline invariant: at any moment a UUID has at most one open interval; real-name intervals fill the gaps between alias intervals. Hooks: `putActive` (open alias), `releaseToCooldownInternal` (close alias + open real-name), `forceClearActive` (same), `recordTrueName` (open real-name on first encounter — covers vanilla-client first login). Sub-100ms intervals dropped at close time to absorb the spurious zero-duration real-name interval produced by `claim()`'s switch sequence.
- **Op-aware real-name targeting** for vanilla commands routing through `EntityArgument` (`/tp`, `/tell`, `/kick`, `/give`, `/list`, etc.). New `CommandContextHolder` (thread-local `CommandSourceStack` stack) populated by a `Commands#performCommand` mixin. `PlayerListMixin#getPlayerByName` consults it: server console / command blocks always bypass the disguise privacy-block; ops bypass when `runner.opLevel >= target.opLevel`. Non-ops still see the alias-only world. **Note:** `/ban`, `/pardon`, `/op`, `/deop`, `/whitelist` go through `GameProfileArgument` → `GameProfileCache#get(String)`, which is intentionally NOT alias-aware — moderation policy is to use `/whois` + `/namehistory` to find the real name, then ban normally (see `memory/project_moderation_policy.md`).

### Phase 4 — storage hygiene
- **Server-side GC** (`SkinStore#gcOrphans`): deletes `<world>/playerdisguise/skins/<hash>.png` files whose hash isn't in `ServerDisguiseState.skinByUuid`. Runs on `ServerStoppingEvent` and every 10 min via `ServerPersistenceHook`. Offline players' blobs naturally drop and are re-uploaded on next join.
- **Client-side LRU** (`ClientSkinCache`): caps `<configdir>/playerdisguise/cache/` at 200 PNGs (~12 MB). `evictIfOverCap()` runs on `setDir` (startup) and after each `store`; oldest-by-mtime evicted first. `load()` touches mtime so frequently-used skins stay.

---

## Remaining

### Documentation
- README: disclose that the mod transmits user-supplied skin PNGs and pseudonyms to servers running this mod (Modrinth rule 1.11).
- In-game first-run notice covering the same disclosure.

### Quality-of-life (deferred)
- **Singleplayer mid-session profile hot-swap.** TODO is locked-per-session everywhere; SP could allow swapping without re-launching the world.
- **Vanilla-client name visibility consistency.** Today vanilla clients see aliases in chat / death / join-leave (server bakes alias into the Component) but real names in nameplate / skin / tab list / auto-complete (those are client-side). Mixed view is confusing. Either make it fully consistent (always real for vanilla) or block vanilla clients from joining a server with disguises active.
- **Configuration-phase skin upload optimization.** Currently the client always bundles skin bytes with `ClientDisguiseChoice` even if the server already has them. Could re-introduce `ServerKnownSkin` (S→C) sent before the request so the client only uploads when needed.

---

## Out of scope (intentional)
- No external skin host / outbound HTTP. Skins flow only between the modded client and modded server.
- Vanilla servers see the player's real identity — by design.
- Vanilla clients on a modded server see a mixed view (chat = alias, visuals = real) — see deferred QoL item above.
