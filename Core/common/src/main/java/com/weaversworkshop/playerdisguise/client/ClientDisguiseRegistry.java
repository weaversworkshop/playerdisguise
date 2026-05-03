package com.weaversworkshop.playerdisguise.client;

import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientDisguiseRegistry {
    public record Entry(@Nullable Component name, @Nullable PlayerSkin skin) {}

    private static final Map<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();

    private ClientDisguiseRegistry() {}

    public static void put(UUID uuid, @Nullable String pseudonym, @Nullable PlayerSkin skin) {
        ENTRIES.put(uuid, new Entry(pseudonym == null ? null : Component.literal(pseudonym), skin));
    }

    public static void remove(UUID uuid) {
        ENTRIES.remove(uuid);
    }

    public static void clear() {
        ENTRIES.clear();
    }

    public static Entry get(UUID uuid) {
        return ENTRIES.get(uuid);
    }

    public static Component name(UUID uuid) {
        Entry e = ENTRIES.get(uuid);
        return e == null ? null : e.name;
    }

    public static void updateSkin(UUID uuid, PlayerSkin skin) {
        Entry cur = ENTRIES.get(uuid);
        if (cur == null) ENTRIES.put(uuid, new Entry(null, skin));
        else ENTRIES.put(uuid, new Entry(cur.name(), skin));
    }

    public static PlayerSkin skin(UUID uuid) {
        Entry e = ENTRIES.get(uuid);
        return e == null ? null : e.skin;
    }
}
