package com.weaversworkshop.playerdisguise.neoforge.server;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import com.weaversworkshop.playerdisguise.neoforge.network.PdNetworkSetup;
import com.weaversworkshop.playerdisguise.server.AliasRegistry;
import com.weaversworkshop.playerdisguise.server.ServerDisguiseState;
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
        String realName = joining.getGameProfile().getName();
        AliasRegistry.get().recordTrueName(joining.getUUID(), realName);

        for (UUID kickedUuid : AliasRegistry.get().takeAliasesMatchingRealName(realName, joining.getUUID())) {
            ServerPlayer kicked = joining.server.getPlayerList().getPlayer(kickedUuid);
            if (kicked != null) {
                kicked.connection.disconnect(net.minecraft.network.chat.Component.literal(
                        "Disguise pseudonym matched a real player who just joined: " + realName));
                PlayerDisguise.LOGGER.info("Kicked {} (alias collided with real player {})",
                        kicked.getGameProfile().getName(), joining.getGameProfile().getName());
            }
        }

        PdNetworkSetup.sendBulkSnapshotTo(joining);

        String alias = AliasRegistry.get().pseudonymOf(joining.getUUID());
        if (alias != null) {
            ServerDisguiseState.Skin sk = ServerDisguiseState.get().skinFor(joining.getUUID());
            String hash = sk == null ? "" : sk.hash();
            String model = sk == null ? "" : sk.model();
            PdNetworkSetup.broadcastUpdate(joining.server, joining.getUUID(), alias, hash, model);
        }
    }
}
