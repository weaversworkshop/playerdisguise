package com.weaversworkshop.playerdisguise.neoforge.network;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import com.weaversworkshop.playerdisguise.client.ClientDisguiseHandler;
import com.weaversworkshop.playerdisguise.net.payload.ClientDisguiseChoice;
import com.weaversworkshop.playerdisguise.net.payload.PlayerDisguiseUpdate;
import com.weaversworkshop.playerdisguise.net.payload.RequestSkinBlob;
import com.weaversworkshop.playerdisguise.net.payload.SkinBlob;
import com.weaversworkshop.playerdisguise.server.AliasRegistry;
import com.weaversworkshop.playerdisguise.server.PendingJoinTracker;
import com.weaversworkshop.playerdisguise.server.ServerDisguiseState;
import com.weaversworkshop.playerdisguise.server.SkinStore;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = PlayerDisguise.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class PdNetworkSetup {
    private PdNetworkSetup() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(ClientDisguiseChoice.TYPE, ClientDisguiseChoice.STREAM_CODEC, PdNetworkSetup::handleChoice);
        registrar.playToServer(RequestSkinBlob.TYPE, RequestSkinBlob.STREAM_CODEC, PdNetworkSetup::handleClientRequestBlob);
        registrar.playToClient(PlayerDisguiseUpdate.TYPE, PlayerDisguiseUpdate.STREAM_CODEC, PdNetworkSetup::handleUpdateOnClient);
        registrar.playToClient(SkinBlob.TYPE, SkinBlob.STREAM_CODEC, PdNetworkSetup::handleBlobOnClient);
    }

    public static void broadcastUpdate(MinecraftServer server, UUID uuid, String pseudonym, String hash, String model) {
        PlayerDisguiseUpdate payload = new PlayerDisguiseUpdate(
                uuid,
                pseudonym == null ? "" : pseudonym,
                hash == null ? "" : hash,
                model == null ? "" : model);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(p, payload);
        }
    }

    private static void announceJoinIfPending(ServerPlayer sp) {
        if (!PendingJoinTracker.clear(sp.getUUID())) return;
        Component msg = Component.translatable("multiplayer.player.joined", sp.getDisplayName())
                .withStyle(ChatFormatting.YELLOW);
        sp.server.getPlayerList().broadcastSystemMessage(msg, false);
    }

    public static void sendBulkSnapshotTo(ServerPlayer recipient) {
        MinecraftServer server = recipient.server;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String alias = AliasRegistry.get().pseudonymOf(p.getUUID());
            if (alias == null) continue;
            ServerDisguiseState.Skin sk = ServerDisguiseState.get().skinFor(p.getUUID());
            String hash = sk == null ? "" : sk.hash();
            String model = sk == null ? "" : sk.model();
            PacketDistributor.sendToPlayer(recipient, new PlayerDisguiseUpdate(p.getUUID(), alias, hash, model));
        }
    }

    private static void handleChoice(ClientDisguiseChoice payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            String requested = payload.pseudonym();
            if (requested == null || requested.isBlank()) {
                AliasRegistry.get().release(sp.getUUID());
                ServerDisguiseState.get().clearSkin(sp.getUUID());
                broadcastUpdate(sp.server, sp.getUUID(), "", "", "");
                announceJoinIfPending(sp);
                PlayerDisguise.LOGGER.info("Player {} joined as real identity", sp.getGameProfile().getName());
                return;
            }

            AliasRegistry.ClaimResult result = AliasRegistry.get().claim(sp.getUUID(), requested);
            switch (result) {
                case ACCEPTED, RESUMED_FROM_COOLDOWN -> {
                    String hash = payload.skinHash() == null ? "" : payload.skinHash();
                    String model = payload.skinModel() == null ? "" : payload.skinModel();
                    if (!hash.isBlank() && payload.skinBytes() != null && payload.skinBytes().length > 0) {
                        if (!SkinStore.get().store(hash, payload.skinBytes())) {
                            sp.sendSystemMessage(Component.literal("Skin rejected by server; using name only."));
                            hash = "";
                            model = "";
                        }
                    } else if (!hash.isBlank() && !SkinStore.get().has(hash)) {
                        sp.sendSystemMessage(Component.literal("Server does not have skin '" + hash + "'; using name only."));
                        hash = "";
                        model = "";
                    }
                    if (!hash.isBlank()) ServerDisguiseState.get().putSkin(sp.getUUID(), hash, model);
                    else ServerDisguiseState.get().clearSkin(sp.getUUID());
                    broadcastUpdate(sp.server, sp.getUUID(), requested, hash, model);
                    announceJoinIfPending(sp);
                    PlayerDisguise.LOGGER.info("Player {} claimed pseudonym '{}' ({}) skin={}",
                            sp.getGameProfile().getName(), requested, result, hash.isBlank() ? "<none>" : hash);
                }
                case REJECTED_ACTIVE_OTHER -> sp.connection.disconnect(
                        Component.literal("Pseudonym '" + requested + "' is in use by another player. Pick a different one and rejoin."));
                case REJECTED_COOLDOWN_OTHER -> sp.connection.disconnect(
                        Component.literal("Pseudonym '" + requested + "' is on cooldown for another player. Pick a different one and rejoin."));
                case REJECTED_REAL_NAME -> sp.connection.disconnect(
                        Component.literal("Pseudonym '" + requested + "' is reserved as another player's real name."));
                case INVALID -> { /* nothing */ }
            }
        });
    }

    private static void handleClientRequestBlob(RequestSkinBlob payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            byte[] bytes = SkinStore.get().load(payload.hash());
            if (bytes == null) {
                PlayerDisguise.LOGGER.warn("Client {} requested skin {} but server has no copy", sp.getGameProfile().getName(), payload.hash());
                return;
            }
            PacketDistributor.sendToPlayer(sp, new SkinBlob(payload.hash(), bytes));
        });
    }

    private static void handleUpdateOnClient(PlayerDisguiseUpdate payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.pseudonym() == null || payload.pseudonym().isBlank()) {
                ClientDisguiseHandler.applyClear(payload.uuid());
            } else {
                ClientDisguiseHandler.applyDisguise(payload.uuid(), payload.pseudonym(), payload.skinHash(), payload.skinModel());
            }
        });
    }

    private static void handleBlobOnClient(SkinBlob payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientDisguiseHandler.onBlobReceived(payload.hash(), payload.bytes()));
    }
}
