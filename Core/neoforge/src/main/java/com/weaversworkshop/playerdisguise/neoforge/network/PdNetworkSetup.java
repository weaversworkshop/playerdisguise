package com.weaversworkshop.playerdisguise.neoforge.network;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import com.weaversworkshop.playerdisguise.net.payload.ClientDisguiseChoice;
import com.weaversworkshop.playerdisguise.server.ServerDisguiseRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = PlayerDisguise.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class PdNetworkSetup {
    private PdNetworkSetup() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(
                ClientDisguiseChoice.TYPE,
                ClientDisguiseChoice.STREAM_CODEC,
                PdNetworkSetup::handleChoice
        );
    }

    private static void handleChoice(ClientDisguiseChoice payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            String requested = payload.pseudonym();
            if (requested == null || requested.isBlank()) {
                ServerDisguiseRegistry.release(sp.getUUID());
                PlayerDisguise.LOGGER.info("Player {} cleared disguise", sp.getGameProfile().getName());
                return;
            }
            ServerDisguiseRegistry.ClaimResult result = ServerDisguiseRegistry.claim(sp.getUUID(), requested);
            switch (result) {
                case ACCEPTED -> PlayerDisguise.LOGGER.info("Player {} claimed pseudonym '{}'",
                        sp.getGameProfile().getName(), requested);
                case ALREADY_CLAIMED -> {
                    sp.sendSystemMessage(Component.literal("Pseudonym '" + requested + "' is already in use; using your real name."));
                    PlayerDisguise.LOGGER.info("Pseudonym '{}' rejected for {} (already claimed)", requested, sp.getGameProfile().getName());
                }
                case INVALID -> { /* nothing to do */ }
            }
        });
    }
}
