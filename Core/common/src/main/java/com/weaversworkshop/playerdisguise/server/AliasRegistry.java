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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

    public record CooldownEntry(String alias, long expiryMs) {}
    private record SaveFile(List<ActiveEntry> active, List<SavedCooldown> cooldown, List<TrueNameEntry> trueNames) {}
    private record ActiveEntry(String uuid, String alias) {}
    private record SavedCooldown(String uuid, String alias, long expiryMs) {}
    private record TrueNameEntry(String uuid, String realName) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AliasRegistry INSTANCE = new AliasRegistry();
    public static AliasRegistry get() { return INSTANCE; }

    private final Map<UUID, String> activeByUuid = new ConcurrentHashMap<>();
    private final Map<String, UUID> activeByAliasLc = new ConcurrentHashMap<>();
    private final Map<UUID, CooldownEntry> cooldownByUuid = new ConcurrentHashMap<>();
    private final Map<String, UUID> cooldownByAliasLc = new ConcurrentHashMap<>();
    private final Map<UUID, String> trueNameByUuid = new ConcurrentHashMap<>();
    private final Map<String, UUID> trueNameByLc = new ConcurrentHashMap<>();

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
            cooldownByUuid.remove(uuid);
            cooldownByAliasLc.remove(lc);
            releaseToCooldownInternal(uuid);
            putActive(uuid, alias);
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
        if (alias != null) activeByAliasLc.remove(alias.toLowerCase(Locale.ROOT));
    }

    public synchronized void recordTrueName(UUID uuid, String realName) {
        if (realName == null || realName.isBlank()) return;
        String prev = trueNameByUuid.put(uuid, realName);
        if (prev != null && !prev.equalsIgnoreCase(realName)) {
            trueNameByLc.remove(prev.toLowerCase(Locale.ROOT));
        }
        trueNameByLc.put(realName.toLowerCase(Locale.ROOT), uuid);
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
        cooldownByUuid.clear();
        cooldownByAliasLc.clear();
        trueNameByUuid.clear();
        trueNameByLc.clear();
    }

    private void putActive(UUID uuid, String alias) {
        activeByUuid.put(uuid, alias);
        activeByAliasLc.put(alias.toLowerCase(Locale.ROOT), uuid);
    }

    private void releaseToCooldownInternal(UUID uuid) {
        String alias = activeByUuid.remove(uuid);
        if (alias == null) return;
        activeByAliasLc.remove(alias.toLowerCase(Locale.ROOT));
        CooldownEntry prior = cooldownByUuid.remove(uuid);
        if (prior != null) cooldownByAliasLc.remove(prior.alias.toLowerCase(Locale.ROOT));
        long expiry = System.currentTimeMillis() + cooldownMs;
        cooldownByUuid.put(uuid, new CooldownEntry(alias, expiry));
        cooldownByAliasLc.put(alias.toLowerCase(Locale.ROOT), uuid);
    }

    public synchronized void load(Path file) {
        clear();
        if (!Files.exists(file)) return;
        try {
            SaveFile sf = GSON.fromJson(Files.readString(file), SaveFile.class);
            if (sf == null) return;
            if (sf.active != null) for (ActiveEntry e : sf.active) {
                if (e == null || e.uuid == null || e.alias == null) continue;
                try { putActive(UUID.fromString(e.uuid), e.alias); } catch (Exception ignored) {}
            }
            if (sf.cooldown != null) for (SavedCooldown e : sf.cooldown) {
                if (e == null || e.uuid == null || e.alias == null) continue;
                try {
                    UUID u = UUID.fromString(e.uuid);
                    cooldownByUuid.put(u, new CooldownEntry(e.alias, e.expiryMs));
                    cooldownByAliasLc.put(e.alias.toLowerCase(Locale.ROOT), u);
                } catch (Exception ignored) {}
            }
            if (sf.trueNames != null) for (TrueNameEntry e : sf.trueNames) {
                if (e == null || e.uuid == null || e.realName == null) continue;
                try { recordTrueName(UUID.fromString(e.uuid), e.realName); } catch (Exception ignored) {}
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
            for (var e : activeByUuid.entrySet()) active.add(new ActiveEntry(e.getKey().toString(), e.getValue()));
            List<SavedCooldown> cooldown = new ArrayList<>();
            for (var e : cooldownByUuid.entrySet()) cooldown.add(new SavedCooldown(e.getKey().toString(), e.getValue().alias, e.getValue().expiryMs));
            List<TrueNameEntry> trueNames = new ArrayList<>();
            for (var e : trueNameByUuid.entrySet()) trueNames.add(new TrueNameEntry(e.getKey().toString(), e.getValue()));
            Files.writeString(file, GSON.toJson(new SaveFile(active, cooldown, trueNames)));
        } catch (IOException e) {
            PlayerDisguise.LOGGER.error("Failed to save alias registry to {}", file, e);
        }
    }

    public synchronized List<String> activeAliasesSnapshot() {
        return Collections.unmodifiableList(new ArrayList<>(activeByUuid.values()));
    }
}
