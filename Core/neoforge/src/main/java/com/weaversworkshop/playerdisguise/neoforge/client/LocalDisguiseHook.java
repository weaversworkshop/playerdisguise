package com.weaversworkshop.playerdisguise.neoforge.client;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import com.weaversworkshop.playerdisguise.client.ClientDisguiseRegistry;
import com.weaversworkshop.playerdisguise.client.PlayerDisguiseClient;
import com.weaversworkshop.playerdisguise.client.skin.SkinLibrary;
import com.weaversworkshop.playerdisguise.client.skin.SkinTextureCache;
import com.weaversworkshop.playerdisguise.config.ConfigStore;
import com.weaversworkshop.playerdisguise.config.PseudonymConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = PlayerDisguise.MODID, value = Dist.CLIENT)
public final class LocalDisguiseHook {
    private LocalDisguiseHook() {}

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.hasSingleplayerServer()) return;

        LocalPlayer p = event.getPlayer();
        if (p == null) return;

        ConfigStore store = PlayerDisguiseClient.config();
        PseudonymConfig cfg = store.current();
        if (!cfg.hasPseudonym() && !cfg.hasSkin()) return;

        PlayerSkin skin = null;
        if (cfg.hasSkin()) {
            try {
                SkinLibrary lib = new SkinLibrary(store.skinsDir());
                lib.refresh();
                SkinLibrary.Entry.Valid v = lib.findByFilename(cfg.skinFileName());
                if (v != null) skin = SkinTextureCache.getOrRegister(v.bytes(), v.sha256());
                else PlayerDisguise.LOGGER.warn("Configured skin '{}' not found in skins folder", cfg.skinFileName());
            } catch (Exception e) {
                PlayerDisguise.LOGGER.warn("Failed to register local skin disguise", e);
            }
        }
        ClientDisguiseRegistry.put(
                p.getUUID(),
                cfg.hasPseudonym() ? cfg.pseudonymName() : null,
                skin
        );
        PlayerDisguise.LOGGER.info("Applied local disguise: name={}, skinFile={}",
                cfg.pseudonymName(), cfg.skinFileName());
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientDisguiseRegistry.clear();
    }
}
