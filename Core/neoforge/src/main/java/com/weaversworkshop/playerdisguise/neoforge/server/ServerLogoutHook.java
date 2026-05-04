package com.weaversworkshop.playerdisguise.neoforge.server;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import com.weaversworkshop.playerdisguise.server.AliasRegistry;
import com.weaversworkshop.playerdisguise.server.ServerDisguiseState;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = PlayerDisguise.MODID)
public final class ServerLogoutHook {
    private ServerLogoutHook() {}

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        AliasRegistry.get().release(sp.getUUID());
        ServerDisguiseState.get().clearSkin(sp.getUUID());
        // Intentionally do NOT broadcast a clear: other clients may still be
        // rendering chat history / lingering references to this player's
        // disguised identity. Connection end clears the client registry wholesale.
    }
}
