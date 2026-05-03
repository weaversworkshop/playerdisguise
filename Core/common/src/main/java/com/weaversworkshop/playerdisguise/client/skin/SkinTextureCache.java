package com.weaversworkshop.playerdisguise.client.skin;

import com.mojang.blaze3d.platform.NativeImage;
import com.weaversworkshop.playerdisguise.PlayerDisguise;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class SkinTextureCache {
    private static final Map<String, ResourceLocation> TEXTURE_BY_HASH = new HashMap<>();
    private static final Map<String, PlayerSkin> SKIN_BY_KEY = new HashMap<>();
    private static PlayerSkin MISSING;

    private SkinTextureCache() {}

    private static String key(String hash, PlayerSkin.Model model) {
        return hash + ":" + (model == PlayerSkin.Model.SLIM ? "slim" : "wide");
    }

    public static PlayerSkin missing() {
        if (MISSING != null) return MISSING;
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(
                PlayerDisguise.MODID, "textures/entity/player/missing.png");
        MISSING = new PlayerSkin(rl, null, null, null, PlayerSkin.Model.WIDE, true);
        return MISSING;
    }

    public static PlayerSkin getOrRegister(byte[] bytes, String hash, PlayerSkin.Model model) throws IOException {
        String k = key(hash, model);
        PlayerSkin cached = SKIN_BY_KEY.get(k);
        if (cached != null) return cached;

        ResourceLocation rl = TEXTURE_BY_HASH.get(hash);
        if (rl == null) {
            NativeImage img;
            try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
                img = NativeImage.read(in);
            }
            DynamicTexture tex = new DynamicTexture(img);
            rl = ResourceLocation.fromNamespaceAndPath(PlayerDisguise.MODID, "skin/" + hash);
            Minecraft.getInstance().getTextureManager().register(rl, tex);
            TEXTURE_BY_HASH.put(hash, rl);
        }
        PlayerSkin skin = new PlayerSkin(rl, null, null, null, model, true);
        SKIN_BY_KEY.put(k, skin);
        return skin;
    }
}
