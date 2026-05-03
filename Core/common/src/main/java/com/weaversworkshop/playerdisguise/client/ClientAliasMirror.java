package com.weaversworkshop.playerdisguise.client;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientAliasMirror {
    private static final Map<UUID, String> BY_UUID = new ConcurrentHashMap<>();
    private static final Map<String, UUID> BY_ALIAS_LC = new ConcurrentHashMap<>();

    private ClientAliasMirror() {}

    public static void put(UUID uuid, @Nullable String alias) {
        String prev = BY_UUID.remove(uuid);
        if (prev != null) BY_ALIAS_LC.remove(prev.toLowerCase(Locale.ROOT));
        if (alias != null && !alias.isBlank()) {
            BY_UUID.put(uuid, alias);
            BY_ALIAS_LC.put(alias.toLowerCase(Locale.ROOT), uuid);
        }
    }

    public static @Nullable String aliasOf(UUID uuid) {
        return BY_UUID.get(uuid);
    }

    public static @Nullable UUID uuidOf(String alias) {
        if (alias == null) return null;
        return BY_ALIAS_LC.get(alias.toLowerCase(Locale.ROOT));
    }

    public static void clear() {
        BY_UUID.clear();
        BY_ALIAS_LC.clear();
    }
}
