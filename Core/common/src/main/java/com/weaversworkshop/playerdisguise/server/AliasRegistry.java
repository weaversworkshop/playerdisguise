package com.weaversworkshop.playerdisguise.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.weaversworkshop.playerdisguise.PlayerDisguise;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AliasRegistry {
    public static final long DEFAULT_COOLDOWN_MS = 60L * 60L * 1000L; // 1 hour

    public enum ClaimResult {
        ACCEPTED,
        RESUMED_FROM_COOLDOWN,
        REJECTED_ACTIVE_OTHER,
        REJECTED_COOLDOWN_OTHER,
        REJECTED_REAL_NAME,
        INVALID
    }

    public record SkinRef(String hash, String model) {}
    public record CooldownEntry(String alias, long expiryMs, @Nullable SkinRef skin) {}
    /**
     * A single interval during which a UUID held a name (alias OR real). {@code endMs == 0L} means still active.
     * {@code realName} is true for periods the player held their real Mojang name (i.e., undisguised).
     */
    public record NameInterval(String alias, long startMs, long endMs, boolean realName) {
        public boolean isOpen() { return endMs == 0L; }
    }
    private record SaveFile(List<ActiveEntry> active, List<SavedCooldown> cooldown, List<TrueNameEntry> trueNames, List<HistoryEntry> history) {}
    private record ActiveEntry(String uuid, String alias, @Nullable String hash, @Nullable String model) {}
    private record SavedCooldown(String uuid, String alias, long expiryMs, @Nullable String hash, @Nullable String model) {}
    private record TrueNameEntry(String uuid, String realName) {}
    private record HistoryEntry(String uuid, String alias, long startMs, long endMs, boolean realName) {}

    /** Intervals shorter than this on close are spurious (config-phase open-then-close in the same call) and dropped. */
    private static final long SHORT_INTERVAL_DROP_MS = 100L;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AliasRegistry INSTANCE = new AliasRegistry();
    public static AliasRegistry get() { return INSTANCE; }

    private final Map<UUID, String> activeByUuid = new ConcurrentHashMap<>();
    private final Map<String, UUID> activeByAliasLc = new ConcurrentHashMap<>();
    /** Skin associated with each active claim. Cleared when alias released; transferred into the cooldown entry. */
    private final Map<UUID, SkinRef> activeSkinByUuid = new ConcurrentHashMap<>();
    private final Map<UUID, CooldownEntry> cooldownByUuid = new ConcurrentHashMap<>();
    private final Map<String, UUID> cooldownByAliasLc = new ConcurrentHashMap<>();
    private final Map<UUID, String> trueNameByUuid = new ConcurrentHashMap<>();
    private final Map<String, UUID> trueNameByLc = new ConcurrentHashMap<>();
    // Persistent historical alias index: every alias every UUID has ever held, with intervals.
    private final Map<UUID, List<NameInterval>> historyByUuid = new ConcurrentHashMap<>();

    private long cooldownMs = DEFAULT_COOLDOWN_MS;

    private AliasRegistry() {}

    public synchronized void setCooldownMs(long ms) { this.cooldownMs = ms; }

    public @Nullable String pseudonymOf(UUID uuid) {
        return activeByUuid.get(uuid);
    }

    public @Nullable UUID uuidOf(String alias) {
        if (alias == null) return null;
        return activeByAliasLc.get(alias.toLowerCase(Locale.ROOT));
    }

    public boolean isDisguised(UUID uuid) {
        return activeByUuid.containsKey(uuid);
    }

    public synchronized ClaimResult claim(UUID uuid, String alias) {
        if (alias == null || alias.isBlank()) return ClaimResult.INVALID;
        String lc = alias.toLowerCase(Locale.ROOT);

        String myActive = activeByUuid.get(uuid);
        if (alias.equalsIgnoreCase(myActive)) return ClaimResult.ACCEPTED;

        CooldownEntry myCool = cooldownByUuid.get(uuid);
        if (myCool != null && lc.equals(myCool.alias.toLowerCase(Locale.ROOT))) {
            SkinRef cooldownSkin = myCool.skin;
            cooldownByUuid.remove(uuid);
            cooldownByAliasLc.remove(lc);
            releaseToCooldownInternal(uuid);
            putActive(uuid, alias);
            // Carry the prior skin forward — the handler may overwrite it via setActiveSkin if the
            // client supplied a different hash, but until then resuming a cooldown alias retains its skin.
            if (cooldownSkin != null) activeSkinByUuid.put(uuid, cooldownSkin);
            return ClaimResult.RESUMED_FROM_COOLDOWN;
        }

        UUID realOwner = trueNameByLc.get(lc);
        if (realOwner != null && !realOwner.equals(uuid)) return ClaimResult.REJECTED_REAL_NAME;

        UUID otherActive = activeByAliasLc.get(lc);
        if (otherActive != null && !otherActive.equals(uuid)) return ClaimResult.REJECTED_ACTIVE_OTHER;

        UUID otherCool = cooldownByAliasLc.get(lc);
        if (otherCool != null && !otherCool.equals(uuid)) {
            CooldownEntry e = cooldownByUuid.get(otherCool);
            if (e != null && e.expiryMs > System.currentTimeMillis()) return ClaimResult.REJECTED_COOLDOWN_OTHER;
            cooldownByUuid.remove(otherCool);
            cooldownByAliasLc.remove(lc);
        }

        releaseToCooldownInternal(uuid);
        putActive(uuid, alias);
        return ClaimResult.ACCEPTED;
    }

    public synchronized void release(UUID uuid) {
        releaseToCooldownInternal(uuid);
    }

    public synchronized void forceClearActive(UUID uuid) {
        String alias = activeByUuid.remove(uuid);
        activeSkinByUuid.remove(uuid);
        if (alias != null) {
            activeByAliasLc.remove(alias.toLowerCase(Locale.ROOT));
            closeOpenInterval(uuid);
            String realName = trueNameByUuid.get(uuid);
            if (realName != null) openInterval(uuid, realName, true);
        }
    }

    public synchronized void recordTrueName(UUID uuid, String realName) {
        if (realName == null || realName.isBlank()) return;
        String prev = trueNameByUuid.put(uuid, realName);
        if (prev != null && !prev.equalsIgnoreCase(realName)) {
            trueNameByLc.remove(prev.toLowerCase(Locale.ROOT));
        }
        trueNameByLc.put(realName.toLowerCase(Locale.ROOT), uuid);
        // First-encounter (or vanilla-client) bookkeeping: ensure the player's identity is represented in
        // history. If they're currently undisguised and have nothing open, open a real-name interval.
        // No-op when an interval is already open or an alias is currently active.
        if (!activeByUuid.containsKey(uuid)) openInterval(uuid, realName, true);
    }

    public synchronized List<UUID> takeAliasesMatchingRealName(String realName, UUID exempt) {
        if (realName == null) return List.of();
        UUID owner = activeByAliasLc.get(realName.toLowerCase(Locale.ROOT));
        if (owner == null || owner.equals(exempt)) return List.of();
        forceClearActive(owner);
        return List.of(owner);
    }

    public synchronized int pruneExpired() {
        long now = System.currentTimeMillis();
        int n = 0;
        var it = cooldownByUuid.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (e.getValue().expiryMs <= now) {
                cooldownByAliasLc.remove(e.getValue().alias.toLowerCase(Locale.ROOT));
                it.remove();
                n++;
            }
        }
        return n;
    }

    public synchronized void clear() {
        activeByUuid.clear();
        activeByAliasLc.clear();
        activeSkinByUuid.clear();
        cooldownByUuid.clear();
        cooldownByAliasLc.clear();
        trueNameByUuid.clear();
        trueNameByLc.clear();
        historyByUuid.clear();
    }

    /** Set or update the skin associated with a player's currently active claim. {@code hash} blank = clear. */
    public synchronized void setActiveSkin(UUID uuid, @Nullable String hash, @Nullable String model) {
        if (hash == null || hash.isBlank()) {
            activeSkinByUuid.remove(uuid);
            return;
        }
        activeSkinByUuid.put(uuid, new SkinRef(hash, model == null ? "" : model));
    }

    public synchronized void clearActiveSkin(UUID uuid) {
        activeSkinByUuid.remove(uuid);
    }

    public @Nullable SkinRef activeSkinOf(UUID uuid) {
        return activeSkinByUuid.get(uuid);
    }

    public @Nullable SkinRef cooldownSkinOf(UUID uuid) {
        CooldownEntry e = cooldownByUuid.get(uuid);
        return e == null ? null : e.skin;
    }

    /** Union of every hash referenced by an active claim or an unexpired cooldown — the GC live set. */
    public synchronized Set<String> liveSkinHashes() {
        Set<String> out = new HashSet<>();
        for (SkinRef r : activeSkinByUuid.values()) {
            if (r != null && r.hash != null && !r.hash.isBlank()) out.add(r.hash);
        }
        for (CooldownEntry e : cooldownByUuid.values()) {
            if (e != null && e.skin != null && e.skin.hash != null && !e.skin.hash.isBlank()) out.add(e.skin.hash);
        }
        return out;
    }

    private void putActive(UUID uuid, String alias) {
        activeByUuid.put(uuid, alias);
        activeByAliasLc.put(alias.toLowerCase(Locale.ROOT), uuid);
        // Close whatever (real-name or prior alias) was open and open this alias's interval.
        closeOpenInterval(uuid);
        openInterval(uuid, alias, false);
    }

    private void releaseToCooldownInternal(UUID uuid) {
        String alias = activeByUuid.remove(uuid);
        SkinRef skin = activeSkinByUuid.remove(uuid);
        if (alias == null) return;
        activeByAliasLc.remove(alias.toLowerCase(Locale.ROOT));
        closeOpenInterval(uuid);
        // Player is now back to their real name — open a real-name interval to fill the gap.
        // (If putActive runs immediately after this — e.g. claim() switch path — that interval will be
        // dropped as a zero-duration spurious open/close by closeOpenInterval.)
        String realName = trueNameByUuid.get(uuid);
        if (realName != null) openInterval(uuid, realName, true);
        CooldownEntry prior = cooldownByUuid.remove(uuid);
        if (prior != null) cooldownByAliasLc.remove(prior.alias.toLowerCase(Locale.ROOT));
        long expiry = System.currentTimeMillis() + cooldownMs;
        // Carry the just-released skin into the cooldown entry so its blob survives orphan GC during
        // the cooldown window — a quick rejoin can resume without re-uploading.
        cooldownByUuid.put(uuid, new CooldownEntry(alias, expiry, skin));
        cooldownByAliasLc.put(alias.toLowerCase(Locale.ROOT), uuid);
    }

    /** Opens a new interval for {@code uuid} only if no other interval is currently open. */
    private void openInterval(UUID uuid, String alias, boolean realName) {
        List<NameInterval> list = historyByUuid.computeIfAbsent(uuid, k -> new ArrayList<>());
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).isOpen()) return; // something already open — don't stack
        }
        list.add(new NameInterval(alias, System.currentTimeMillis(), 0L, realName));
    }

    /**
     * Closes whichever interval is currently open for {@code uuid} (at most one is). If the open interval
     * is shorter than {@link #SHORT_INTERVAL_DROP_MS} it is removed entirely rather than recorded — this
     * suppresses the spurious zero-duration real-name interval produced by the
     * {@code releaseToCooldownInternal → putActive} sequence inside {@link #claim}.
     */
    private void closeOpenInterval(UUID uuid) {
        List<NameInterval> list = historyByUuid.get(uuid);
        if (list == null) return;
        long now = System.currentTimeMillis();
        for (int i = list.size() - 1; i >= 0; i--) {
            NameInterval ni = list.get(i);
            if (!ni.isOpen()) continue;
            if (now - ni.startMs < SHORT_INTERVAL_DROP_MS) {
                list.remove(i);
            } else {
                list.set(i, new NameInterval(ni.alias, ni.startMs, now, ni.realName));
            }
            return;
        }
    }

    public synchronized void load(Path file) {
        clear();
        if (!Files.exists(file)) return;
        try {
            SaveFile sf = GSON.fromJson(Files.readString(file), SaveFile.class);
            if (sf == null) return;
            if (sf.active != null) for (ActiveEntry e : sf.active) {
                if (e == null || e.uuid == null || e.alias == null) continue;
                try {
                    UUID u = UUID.fromString(e.uuid);
                    putActive(u, e.alias);
                    if (e.hash != null && !e.hash.isBlank()) {
                        activeSkinByUuid.put(u, new SkinRef(e.hash, e.model == null ? "" : e.model));
                    }
                } catch (Exception ignored) {}
            }
            if (sf.cooldown != null) for (SavedCooldown e : sf.cooldown) {
                if (e == null || e.uuid == null || e.alias == null) continue;
                try {
                    UUID u = UUID.fromString(e.uuid);
                    SkinRef skin = (e.hash != null && !e.hash.isBlank())
                            ? new SkinRef(e.hash, e.model == null ? "" : e.model) : null;
                    cooldownByUuid.put(u, new CooldownEntry(e.alias, e.expiryMs, skin));
                    cooldownByAliasLc.put(e.alias.toLowerCase(Locale.ROOT), u);
                } catch (Exception ignored) {}
            }
            if (sf.trueNames != null) for (TrueNameEntry e : sf.trueNames) {
                if (e == null || e.uuid == null || e.realName == null) continue;
                try { recordTrueName(UUID.fromString(e.uuid), e.realName); } catch (Exception ignored) {}
            }
            if (sf.history != null) for (HistoryEntry e : sf.history) {
                if (e == null || e.uuid == null || e.alias == null) continue;
                try {
                    UUID u = UUID.fromString(e.uuid);
                    historyByUuid.computeIfAbsent(u, k -> new ArrayList<>())
                            .add(new NameInterval(e.alias, e.startMs, e.endMs, e.realName));
                } catch (Exception ignored) {}
            }
            int pruned = pruneExpired();
            PlayerDisguise.LOGGER.info("AliasRegistry loaded: {} active, {} cooldown, {} true names ({} expired pruned)",
                    activeByUuid.size(), cooldownByUuid.size(), trueNameByUuid.size(), pruned);
        } catch (IOException e) {
            PlayerDisguise.LOGGER.error("Failed to load alias registry", e);
        }
    }

    public synchronized void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            List<ActiveEntry> active = new ArrayList<>();
            for (var e : activeByUuid.entrySet()) {
                SkinRef skin = activeSkinByUuid.get(e.getKey());
                active.add(new ActiveEntry(
                        e.getKey().toString(), e.getValue(),
                        skin == null ? null : skin.hash, skin == null ? null : skin.model));
            }
            List<SavedCooldown> cooldown = new ArrayList<>();
            for (var e : cooldownByUuid.entrySet()) {
                CooldownEntry ce = e.getValue();
                cooldown.add(new SavedCooldown(
                        e.getKey().toString(), ce.alias, ce.expiryMs,
                        ce.skin == null ? null : ce.skin.hash, ce.skin == null ? null : ce.skin.model));
            }
            List<TrueNameEntry> trueNames = new ArrayList<>();
            for (var e : trueNameByUuid.entrySet()) trueNames.add(new TrueNameEntry(e.getKey().toString(), e.getValue()));
            List<HistoryEntry> history = new ArrayList<>();
            for (var e : historyByUuid.entrySet()) {
                String uuidStr = e.getKey().toString();
                for (NameInterval ni : e.getValue()) history.add(new HistoryEntry(uuidStr, ni.alias, ni.startMs, ni.endMs, ni.realName));
            }
            Files.writeString(file, GSON.toJson(new SaveFile(active, cooldown, trueNames, history)));
        } catch (IOException e) {
            PlayerDisguise.LOGGER.error("Failed to save alias registry to {}", file, e);
        }
    }

    public synchronized List<String> activeAliasesSnapshot() {
        return Collections.unmodifiableList(new ArrayList<>(activeByUuid.values()));
    }

    public @Nullable String realNameOf(UUID uuid) {
        return trueNameByUuid.get(uuid);
    }

    public @Nullable UUID uuidByRealName(String name) {
        if (name == null) return null;
        return trueNameByLc.get(name.toLowerCase(Locale.ROOT));
    }

    /** Full alias history for a UUID, sorted oldest→newest. Open intervals (still active) appear last with {@code endMs == 0}. */
    public synchronized List<NameInterval> historyOf(UUID uuid) {
        List<NameInterval> list = historyByUuid.get(uuid);
        if (list == null || list.isEmpty()) return List.of();
        List<NameInterval> copy = new ArrayList<>(list);
        copy.sort((a, b) -> Long.compare(a.startMs, b.startMs));
        return copy;
    }

    /** Holders of a given alias (case-insensitive): every UUID who has ever held it, with the matching intervals. */
    public synchronized Map<UUID, List<NameInterval>> holdersOfAlias(String alias) {
        if (alias == null) return Map.of();
        String lc = alias.toLowerCase(Locale.ROOT);
        Map<UUID, List<NameInterval>> result = new java.util.LinkedHashMap<>();
        for (var entry : historyByUuid.entrySet()) {
            List<NameInterval> matched = new ArrayList<>();
            for (NameInterval ni : entry.getValue()) {
                if (ni.alias.toLowerCase(Locale.ROOT).equals(lc)) matched.add(ni);
            }
            if (!matched.isEmpty()) {
                matched.sort((a, b) -> Long.compare(a.startMs, b.startMs));
                result.put(entry.getKey(), matched);
            }
        }
        return result;
    }

    /**
     * Resolve a name to a target UUID for /namehistory.
     * Priority: current alias > current real name > most-recent historical alias use.
     */
    public synchronized @Nullable UUID resolveTarget(String name) {
        if (name == null || name.isBlank()) return null;
        String lc = name.toLowerCase(Locale.ROOT);
        UUID byActive = activeByAliasLc.get(lc);
        if (byActive != null) return byActive;
        UUID byReal = trueNameByLc.get(lc);
        if (byReal != null) return byReal;
        UUID best = null;
        long bestEnd = Long.MIN_VALUE;
        long now = System.currentTimeMillis();
        for (var entry : historyByUuid.entrySet()) {
            for (NameInterval ni : entry.getValue()) {
                if (!ni.alias.toLowerCase(Locale.ROOT).equals(lc)) continue;
                long e = ni.isOpen() ? now : ni.endMs;
                if (e > bestEnd) { bestEnd = e; best = entry.getKey(); }
            }
        }
        return best;
    }
}
