package com.weaversworkshop.playerdisguise.server;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerDisguiseRegistry {
    private static final Map<UUID, String> BY_UUID = new ConcurrentHashMap<>();
    private static final Map<String, UUID> BY_PSEUDONYM_LC = new ConcurrentHashMap<>();

    private ServerDisguiseRegistry() {}

    public enum ClaimResult { ACCEPTED, ALREADY_CLAIMED, INVALID }

    public static synchronized ClaimResult claim(UUID uuid, String pseudonym) {
        if (pseudonym == null || pseudonym.isBlank()) return ClaimResult.INVALID;
        String key = pseudonym.toLowerCase(Locale.ROOT);
        UUID owner = BY_PSEUDONYM_LC.get(key);
        if (owner != null && !owner.equals(uuid)) return ClaimResult.ALREADY_CLAIMED;
        release(uuid);
        BY_UUID.put(uuid, pseudonym);
        BY_PSEUDONYM_LC.put(key, uuid);
        return ClaimResult.ACCEPTED;
    }

    public static synchronized void release(UUID uuid) {
        String prev = BY_UUID.remove(uuid);
        if (prev != null) BY_PSEUDONYM_LC.remove(prev.toLowerCase(Locale.ROOT));
    }

    public static @Nullable String pseudonymOf(UUID uuid) {
        return BY_UUID.get(uuid);
    }

    public static @Nullable UUID uuidOf(String pseudonym) {
        if (pseudonym == null) return null;
        return BY_PSEUDONYM_LC.get(pseudonym.toLowerCase(Locale.ROOT));
    }

    public static boolean isDisguised(UUID uuid) {
        return BY_UUID.containsKey(uuid);
    }
}
