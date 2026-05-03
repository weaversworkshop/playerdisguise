package com.weaversworkshop.playerdisguise.neoforge.server;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import com.weaversworkshop.playerdisguise.neoforge.network.PdNetworkSetup;
import com.weaversworkshop.playerdisguise.server.AliasRegistry;
import com.weaversworkshop.playerdisguise.server.PendingJoinTracker;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.UUID;

@EventBusSubscriber(modid = PlayerDisguise.MODID)
public final class ServerLoginHook {
    private ServerLoginHook() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer joining)) return;
        PendingJoinTracker.mark(joining.getUUID());
        String realName = joining.getGameProfile().getName();
        AliasRegistry.get().recordTrueName(joining.getUUID(), realName);
        for (UUID kickedUuid : AliasRegistry.get().takeAliasesMatchingRealName(realName, joining.getUUID())) {
            ServerPlayer kicked = joining.server.getPlayerList().getPlayer(kickedUuid);
            if (kicked != null) {
                kicked.connection.disconnect(Component.literal(
                        "Disguise pseudonym matched a real player who just joined: " + realName));
                PlayerDisguise.LOGGER.info("Kicked {} (alias '{}' collided with real player {})",
                        kicked.getGameProfile().getName(), realName, joining.getGameProfile().getName());
            }
        }
        PdNetworkSetup.sendBulkSnapshotTo(joining);
    }
}
