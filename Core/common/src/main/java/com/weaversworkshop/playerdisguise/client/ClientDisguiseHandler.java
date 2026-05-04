package com.weaversworkshop.playerdisguise.client;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import com.weaversworkshop.playerdisguise.client.skin.ClientSkinCache;
import com.weaversworkshop.playerdisguise.client.skin.SkinTextureCache;
import net.minecraft.client.resources.PlayerSkin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ClientDisguiseHandler {
    public record PendingEntry(UUID uuid, PlayerSkin.Model model) {}

    private static final Map<String, List<PendingEntry>> PENDING = new ConcurrentHashMap<>();
    private static volatile Consumer<String> blobRequester = h -> {};

    private ClientDisguiseHandler() {}

    public static void setBlobRequester(Consumer<String> requester) { blobRequester = requester; }

    public static void requestBlob(String hash) {
        if (hash != null && !hash.isBlank()) blobRequester.accept(hash);
    }

    public static void applyDisguise(UUID uuid, String pseudonym, String hash, String modelId) {
        ClientAliasMirror.put(uuid, pseudonym);
        ClientDisguiseRegistry.put(uuid, (pseudonym == null || pseudonym.isBlank()) ? null : pseudonym, null);

        if (hash == null || hash.isBlank()) {
            ClientDisguiseRegistry.updateSkin(uuid, null);
            return;
        }
        PlayerSkin.Model model = PlayerSkin.Model.byName(modelId);
        byte[] cached = ClientSkinCache.get().load(hash);
        if (cached != null) {
            try {
                PlayerSkin skin = SkinTextureCache.getOrRegister(cached, hash, model);
                ClientDisguiseRegistry.updateSkin(uuid, skin);
                return;
            } catch (Exception e) {
                PlayerDisguise.LOGGER.warn("Failed to register cached skin {}", hash, e);
            }
        }
        PENDING.computeIfAbsent(hash, k -> new ArrayList<>()).add(new PendingEntry(uuid, model));
        blobRequester.accept(hash);
    }

    public static void applyClear(UUID uuid) {
        ClientAliasMirror.put(uuid, null);
        ClientDisguiseRegistry.remove(uuid);
    }

    public static void onBlobReceived(String hash, byte[] bytes) {
        if (!ClientSkinCache.get().store(hash, bytes)) return;
        List<PendingEntry> waiting = PENDING.remove(hash);
        if (waiting == null) return;
        for (PendingEntry e : waiting) {
            try {
                PlayerSkin skin = SkinTextureCache.getOrRegister(bytes, hash, e.model);
                ClientDisguiseRegistry.updateSkin(e.uuid, skin);
            } catch (Exception ex) {
                PlayerDisguise.LOGGER.warn("Failed to register received skin {} for {}", hash, e.uuid, ex);
            }
        }
    }

    public static void clearAll() {
        PENDING.clear();
    }
}
