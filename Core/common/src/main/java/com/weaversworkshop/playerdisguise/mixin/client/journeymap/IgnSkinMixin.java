package com.weaversworkshop.playerdisguise.mixin.client.journeymap;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import com.weaversworkshop.playerdisguise.client.ClientDisguiseRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mixin(targets = "journeymap.client.texture.IgnSkin", remap = false)
public abstract class IgnSkinMixin {

    @Shadow(remap = false)
    public static native DynamicTexture cropToFace(NativeImage img);

    private static final Map<UUID, ResourceLocation> playerdisguise$faceRl = new HashMap<>();
    private static final Map<UUID, DynamicTexture> playerdisguise$faceTex = new HashMap<>();

    @Inject(method = "getFace", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void playerdisguise$getFace(GameProfile profile, CallbackInfoReturnable<DynamicTexture> cir) {
        if (profile == null) return;
        UUID uuid = profile.getId();
        if (uuid == null) return;
        PlayerSkin skin = ClientDisguiseRegistry.skin(uuid);
        if (skin == null) return;
        ResourceLocation rl = skin.texture();
        if (rl == null) return;

        DynamicTexture cached = playerdisguise$faceTex.get(uuid);
        if (cached != null && rl.equals(playerdisguise$faceRl.get(uuid))) {
            cir.setReturnValue(cached);
            return;
        }

        AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(rl, null);
        if (!(tex instanceof DynamicTexture dt)) return;
        NativeImage img = dt.getPixels();
        if (img == null) return;

        DynamicTexture face;
        try {
            face = cropToFace(img);
        } catch (Throwable t) {
            return;
        }
        if (face == null) return;

        DynamicTexture old = playerdisguise$faceTex.put(uuid, face);
        playerdisguise$faceRl.put(uuid, rl);
        if (old != null) {
            try { old.close(); } catch (Throwable ignored) {}
        }
        cir.setReturnValue(face);
    }
}
