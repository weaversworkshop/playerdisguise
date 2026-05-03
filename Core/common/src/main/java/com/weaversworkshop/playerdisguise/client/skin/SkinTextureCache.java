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
    private static final Map<String, PlayerSkin> CACHE = new HashMap<>();
    private static PlayerSkin MISSING;

    private SkinTextureCache() {}

    public static PlayerSkin missing() {
        if (MISSING != null) return MISSING;
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(
                PlayerDisguise.MODID, "textures/entity/player/missing.png");
        MISSING = new PlayerSkin(rl, null, null, null, PlayerSkin.Model.WIDE, true);
        return MISSING;
    }

    public static PlayerSkin getOrRegister(byte[] bytes, String hash) throws IOException {
        PlayerSkin cached = CACHE.get(hash);
        if (cached != null) return cached;

        NativeImage img;
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            img = NativeImage.read(in);
        }
        DynamicTexture tex = new DynamicTexture(img);
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(PlayerDisguise.MODID, "skin/" + hash);
        Minecraft.getInstance().getTextureManager().register(rl, tex);
        PlayerSkin skin = new PlayerSkin(rl, null, null, null, PlayerSkin.Model.WIDE, true);
        CACHE.put(hash, skin);
        return skin;
    }

    public static PlayerSkin get(String hash) {
        return CACHE.get(hash);
    }
}
