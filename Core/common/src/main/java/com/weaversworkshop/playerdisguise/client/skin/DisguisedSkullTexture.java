package com.weaversworkshop.playerdisguise.client.skin;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import com.weaversworkshop.playerdisguise.client.ClientDisguiseHandler;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public final class DisguisedSkullTexture {
    private DisguisedSkullTexture() {}

    /**
     * Resolves the skin texture for a disguised dropped head. Returns null if the
     * skin blob is not yet cached locally; in that case a fetch is requested so a
     * later render call will succeed.
     */
    public static @Nullable ResourceLocation resolve(String hash, String modelStr) {
        if (hash == null || hash.isBlank()) return null;
        PlayerSkin.Model model = PlayerSkin.Model.byName(modelStr == null ? "wide" : modelStr);
        byte[] bytes = ClientSkinCache.get().load(hash);
        if (bytes == null) {
            ClientDisguiseHandler.requestBlob(hash);
            return null;
        }
        try {
            return SkinTextureCache.getOrRegister(bytes, hash, model).texture();
        } catch (Exception e) {
            PlayerDisguise.LOGGER.warn("Failed to register disguised head texture {}", hash, e);
            return null;
        }
    }
}
