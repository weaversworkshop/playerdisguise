package com.weaversworkshop.playerdisguise.neoforge.client;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import com.weaversworkshop.playerdisguise.client.ClientAliasMirror;
import com.weaversworkshop.playerdisguise.client.ClientDisguiseHandler;
import com.weaversworkshop.playerdisguise.client.ClientDisguiseRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = PlayerDisguise.MODID, value = Dist.CLIENT)
public final class LocalDisguiseHook {
    private LocalDisguiseHook() {}

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientDisguiseRegistry.clear();
        ClientAliasMirror.clear();
        ClientDisguiseHandler.clearAll();
    }
}
