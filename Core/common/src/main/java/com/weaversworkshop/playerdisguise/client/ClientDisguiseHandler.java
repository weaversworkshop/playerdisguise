package com.weaversworkshop.playerdisguise.client;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import com.weaversworkshop.playerdisguise.client.skin.ClientSkinCache;
import com.weaversworkshop.playerdisguise.client.skin.SkinLibrary;
import com.weaversworkshop.playerdisguise.client.skin.SkinTextureCache;
import com.weaversworkshop.playerdisguise.config.ConfigStore;
import com.weaversworkshop.playerdisguise.config.Profile;
import com.weaversworkshop.playerdisguise.config.ProfileBook;
import com.weaversworkshop.playerdisguise.net.payload.ClientSkinUpload;
import com.weaversworkshop.playerdisguise.net.payload.ProceedWithoutSkin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

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

    /**
     * Server requested an upload of a skin we declared. Reads the bytes for the active profile's skin and
     * verifies the hash matches what the server asked for, then sends {@link ClientSkinUpload}. If we
     * can't produce matching bytes, we send back an empty upload — server will reject it via the failure
     * path, which then prompts the user.
     */
    public static void respondToServerSkinRequest(String hash, Consumer<CustomPacketPayload> reply) {
        byte[] bytes = readActiveSkinBytesFor(hash);
        if (bytes == null) {
            PlayerDisguise.LOGGER.warn("Server asked for skin {} but we can't produce it locally", hash);
            // Send an empty upload — server validates and routes us to the SkinUploadFailed flow.
            reply.accept(new ClientSkinUpload(hash, new byte[0]));
            return;
        }
        reply.accept(new ClientSkinUpload(hash, bytes));
    }

    private static byte @org.jetbrains.annotations.Nullable [] readActiveSkinBytesFor(String hash) {
        try {
            ConfigStore store = PlayerDisguiseClient.config();
            ProfileBook book = store.book();
            if (book.isRealActive()) return null;
            Profile active = book.activeStored();
            if (!active.hasSkin()) return null;
            SkinLibrary lib = new SkinLibrary(store.skinsDir());
            lib.refresh();
            SkinLibrary.Entry.Valid v = lib.findByFilename(active.skinFileName());
            if (v == null) return null;
            if (!hash.equals(v.sha256())) return null;
            return v.bytes();
        } catch (Exception e) {
            PlayerDisguise.LOGGER.warn("Failed to read active skin for upload", e);
            return null;
        }
    }

    /**
     * Server reported the skin upload failed. Show a ConfirmScreen warning the user that proceeding will
     * expose their real skin. Cancel disconnects; Proceed sends {@link ProceedWithoutSkin}.
     */
    public static void showSkinUploadFailedScreen(String reason, Consumer<CustomPacketPayload> reply, Consumer<Component> disconnect) {
        Minecraft mc = Minecraft.getInstance();
        Screen prior = mc.screen;
        String reasonText = (reason == null || reason.isBlank()) ? "(no reason given)" : reason;
        Component title = Component.literal("Disguise skin upload failed");
        Component body = Component.literal(
                reasonText
                        + "\n\nProceeding will join the server with your alias, but your real Mojang skin will be visible to other players. "
                        + "Cancelling will disconnect you so you can fix the skin and try again.");
        mc.execute(() -> mc.setScreen(new ConfirmScreen(
                yes -> {
                    if (yes) {
                        reply.accept(ProceedWithoutSkin.INSTANCE);
                        mc.setScreen(prior);
                    } else {
                        disconnect.accept(Component.literal("Disguise skin upload failed; you cancelled the join."));
                    }
                },
                title,
                body,
                CommonComponents.GUI_PROCEED,
                CommonComponents.GUI_CANCEL
        )));
    }
}
