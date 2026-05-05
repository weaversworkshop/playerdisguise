package com.weaversworkshop.playerdisguise.server;

import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerDisguiseState {
    public record Skin(String hash, String model) {}

    private static final ServerDisguiseState INSTANCE = new ServerDisguiseState();
    public static ServerDisguiseState get() { return INSTANCE; }

    private final Map<UUID, Skin> skinByUuid = new ConcurrentHashMap<>();

    private ServerDisguiseState() {}

    public void putSkin(UUID uuid, String hash, String model) {
        skinByUuid.put(uuid, new Skin(hash, model));
    }

    public void clearSkin(UUID uuid) {
        skinByUuid.remove(uuid);
    }

    public @Nullable Skin skinFor(UUID uuid) {
        return skinByUuid.get(uuid);
    }

    public void clearAll() {
        skinByUuid.clear();
    }

    public Set<String> liveSkinHashes() {
        Set<String> hashes = new HashSet<>(skinByUuid.size());
        for (Skin s : skinByUuid.values()) {
            if (s != null && s.hash() != null) hashes.add(s.hash());
        }
        return hashes;
    }
}
