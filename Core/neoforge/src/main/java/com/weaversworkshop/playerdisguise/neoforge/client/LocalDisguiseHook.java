package com.weaversworkshop.playerdisguise.neoforge.client;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import com.weaversworkshop.playerdisguise.client.ClientAliasMirror;
import com.weaversworkshop.playerdisguise.client.ClientDisguiseHandler;
import com.weaversworkshop.playerdisguise.client.ClientDisguiseRegistry;
import com.weaversworkshop.playerdisguise.client.PlayerDisguiseClient;
import com.weaversworkshop.playerdisguise.client.skin.SkinLibrary;
import com.weaversworkshop.playerdisguise.config.ConfigStore;
import com.weaversworkshop.playerdisguise.config.Profile;
import com.weaversworkshop.playerdisguise.config.ProfileBook;
import com.weaversworkshop.playerdisguise.net.payload.ClientDisguiseChoice;
import net.minecraft.client.player.LocalPlayer;
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
        LocalPlayer p = event.getPlayer();
        if (p == null) return;

        ConfigStore store = PlayerDisguiseClient.config();
        ProfileBook book = store.book();
        if (book.isRealActive()) {
            PacketDistributor.sendToServer(new ClientDisguiseChoice("", "", "", new byte[0]));
            return;
        }

        Profile active = book.activeStored();
        String hash = "";
        String model = "";
        byte[] bytes = new byte[0];
        if (active.hasSkin()) {
            try {
                SkinLibrary lib = new SkinLibrary(store.skinsDir());
                lib.refresh();
                SkinLibrary.Entry.Valid v = lib.findByFilename(active.skinFileName());
                if (v != null) {
                    hash = v.sha256();
                    model = active.resolvedModel() == net.minecraft.client.resources.PlayerSkin.Model.SLIM ? "slim" : "wide";
                    bytes = v.bytes();
                } else {
                    PlayerDisguise.LOGGER.warn("Active profile '{}' references missing skin '{}'", active.name(), active.skinFileName());
                }
            } catch (Exception e) {
                PlayerDisguise.LOGGER.warn("Failed to read skin for upload", e);
            }
        }
        PacketDistributor.sendToServer(new ClientDisguiseChoice(active.name(), hash, model, bytes));
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientDisguiseRegistry.clear();
        ClientAliasMirror.clear();
        ClientDisguiseHandler.clearAll();
    }
}
