package com.weaversworkshop.playerdisguise.neoforge.client;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import com.weaversworkshop.playerdisguise.client.ClientDisguiseRegistry;
import com.weaversworkshop.playerdisguise.client.PlayerDisguiseClient;
import com.weaversworkshop.playerdisguise.client.skin.SkinLibrary;
import com.weaversworkshop.playerdisguise.client.skin.SkinTextureCache;
import com.weaversworkshop.playerdisguise.config.ConfigStore;
import com.weaversworkshop.playerdisguise.config.Profile;
import com.weaversworkshop.playerdisguise.config.ProfileBook;
import com.weaversworkshop.playerdisguise.net.payload.ClientDisguiseChoice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = PlayerDisguise.MODID, value = Dist.CLIENT)
public final class LocalDisguiseHook {
    private LocalDisguiseHook() {}

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = event.getPlayer();
        if (p == null) return;

        ConfigStore store = PlayerDisguiseClient.config();
        ProfileBook book = store.book();
        if (book.isRealActive()) return;

        Profile active = book.activeStored();

        PacketDistributor.sendToServer(new ClientDisguiseChoice(active.name()));

        if (mc.hasSingleplayerServer()) {
            PlayerSkin skin = null;
            if (active.hasSkin()) {
                try {
                    SkinLibrary lib = new SkinLibrary(store.skinsDir());
                    lib.refresh();
                    SkinLibrary.Entry.Valid v = lib.findByFilename(active.skinFileName());
                    if (v != null) skin = SkinTextureCache.getOrRegister(v.bytes(), v.sha256());
                    else PlayerDisguise.LOGGER.warn("Active profile '{}' references missing skin '{}'", active.name(), active.skinFileName());
                } catch (Exception e) {
                    PlayerDisguise.LOGGER.warn("Failed to register local skin disguise", e);
                }
            }
            ClientDisguiseRegistry.put(p.getUUID(), active.name(), skin);
            PlayerDisguise.LOGGER.info("Applied local disguise: profile='{}', skinFile={}", active.name(), active.skinFileName());
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientDisguiseRegistry.clear();
    }
}
