package com.weaversworkshop.playerdisguise.neoforge.network;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import com.weaversworkshop.playerdisguise.net.payload.ServerRequestDisguise;
import com.weaversworkshop.playerdisguise.server.AliasClaimTask;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;

@EventBusSubscriber(modid = PlayerDisguise.MODID)
public final class RegisterConfigTasks {
    private RegisterConfigTasks() {}

    @SubscribeEvent
    public static void register(RegisterConfigurationTasksEvent event) {
        if (!event.getListener().hasChannel(ServerRequestDisguise.TYPE)) {
            return;
        }
        event.register(new AliasClaimTask());
    }
}
