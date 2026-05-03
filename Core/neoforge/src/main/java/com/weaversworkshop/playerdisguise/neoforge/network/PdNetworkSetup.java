package com.weaversworkshop.playerdisguise.neoforge.network;

import com.mojang.authlib.GameProfile;
import com.weaversworkshop.playerdisguise.PlayerDisguise;
import com.weaversworkshop.playerdisguise.client.ClientDisguiseHandler;
import com.weaversworkshop.playerdisguise.client.PlayerDisguiseClient;
import com.weaversworkshop.playerdisguise.client.skin.SkinLibrary;
import com.weaversworkshop.playerdisguise.config.ConfigStore;
import com.weaversworkshop.playerdisguise.config.Profile;
import com.weaversworkshop.playerdisguise.config.ProfileBook;
import com.weaversworkshop.playerdisguise.net.payload.ClientDisguiseChoice;
import com.weaversworkshop.playerdisguise.net.payload.PlayerDisguiseUpdate;
import com.weaversworkshop.playerdisguise.net.payload.RequestSkinBlob;
import com.weaversworkshop.playerdisguise.net.payload.ServerRequestDisguise;
import com.weaversworkshop.playerdisguise.net.payload.SkinBlob;
import com.weaversworkshop.playerdisguise.server.AliasClaimTask;
import com.weaversworkshop.playerdisguise.server.AliasRegistry;
import com.weaversworkshop.playerdisguise.server.ServerDisguiseState;
import com.weaversworkshop.playerdisguise.server.SkinStore;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.UUID;

@EventBusSubscriber(modid = PlayerDisguise.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class PdNetworkSetup {
    private PdNetworkSetup() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();

        // Configuration phase — alias handshake
        registrar.configurationToClient(
                ServerRequestDisguise.TYPE, ServerRequestDisguise.STREAM_CODEC, PdNetworkSetup::handleRequestOnClient);
        registrar.configurationToServer(
                ClientDisguiseChoice.TYPE, ClientDisguiseChoice.STREAM_CODEC, PdNetworkSetup::handleChoiceConfig);

        // Play phase — disguise broadcasts and skin blob exchange
        registrar.playToClient(
                PlayerDisguiseUpdate.TYPE, PlayerDisguiseUpdate.STREAM_CODEC, PdNetworkSetup::handleUpdateOnClient);
        registrar.playToClient(
                SkinBlob.TYPE, SkinBlob.STREAM_CODEC, PdNetworkSetup::handleBlobOnClient);
        registrar.playToServer(
                RequestSkinBlob.TYPE, RequestSkinBlob.STREAM_CODEC, PdNetworkSetup::handleClientRequestBlob);
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

    private static void handleChoiceConfig(ClientDisguiseChoice payload, IPayloadContext context) {
        if (!(context.listener() instanceof ServerConfigurationPacketListenerImpl listener)) {
            context.disconnect(Component.literal("Disguise handshake received outside configuration phase"));
            return;
        }
        GameProfile profile = listener.playerProfile();
        if (profile == null) {
            context.disconnect(Component.literal("Disguise handshake received without identity"));
            return;
        }
        UUID uuid = profile.getId();
        String realName = profile.getName();
        AliasRegistry.get().recordTrueName(uuid, realName);

        String requested = payload.pseudonym();
        if (requested == null || requested.isBlank()) {
            AliasRegistry.get().release(uuid);
            ServerDisguiseState.get().clearSkin(uuid);
            context.finishCurrentTask(AliasClaimTask.TYPE);
            return;
        }

        AliasRegistry.ClaimResult result = AliasRegistry.get().claim(uuid, requested);
        switch (result) {
            case ACCEPTED, RESUMED_FROM_COOLDOWN -> {
                String hash = payload.skinHash() == null ? "" : payload.skinHash();
                String model = payload.skinModel() == null ? "" : payload.skinModel();
                if (!hash.isBlank() && payload.skinBytes() != null && payload.skinBytes().length > 0) {
                    if (!SkinStore.get().store(hash, payload.skinBytes())) {
                        hash = "";
                        model = "";
                    }
                } else if (!hash.isBlank() && !SkinStore.get().has(hash)) {
                    hash = "";
                    model = "";
                }
                if (!hash.isBlank()) ServerDisguiseState.get().putSkin(uuid, hash, model);
                else ServerDisguiseState.get().clearSkin(uuid);
                PlayerDisguise.LOGGER.info("Player {} claimed pseudonym '{}' ({}) skin={}",
                        realName, requested, result, hash.isBlank() ? "<none>" : hash);
                context.finishCurrentTask(AliasClaimTask.TYPE);
            }
            case REJECTED_ACTIVE_OTHER -> context.disconnect(Component.literal(
                    "Pseudonym '" + requested + "' is in use by another player. Pick a different one and rejoin."));
            case REJECTED_COOLDOWN_OTHER -> context.disconnect(Component.literal(
                    "Pseudonym '" + requested + "' is on cooldown for another player. Pick a different one and rejoin."));
            case REJECTED_REAL_NAME -> context.disconnect(Component.literal(
                    "Pseudonym '" + requested + "' is reserved as another player's real name."));
            case INVALID -> context.disconnect(Component.literal("Invalid pseudonym."));
        }
    }

    private static void handleRequestOnClient(ServerRequestDisguise payload, IPayloadContext context) {
        ConfigStore store = PlayerDisguiseClient.config();
        ProfileBook book = store.book();
        String name = "";
        String hash = "";
        String model = "";
        byte[] bytes = new byte[0];
        if (!book.isRealActive()) {
            Profile active = book.activeStored();
            name = active.name();
            if (active.hasSkin()) {
                try {
                    SkinLibrary lib = new SkinLibrary(store.skinsDir());
                    lib.refresh();
                    SkinLibrary.Entry.Valid v = lib.findByFilename(active.skinFileName());
                    if (v != null) {
                        hash = v.sha256();
                        model = active.resolvedModel() == PlayerSkin.Model.SLIM ? "slim" : "wide";
                        bytes = v.bytes();
                    }
                } catch (Exception e) {
                    PlayerDisguise.LOGGER.warn("Failed to read skin for upload", e);
                }
            }
        }
        context.reply(new ClientDisguiseChoice(name, hash, model, bytes));
    }

    private static void handleClientRequestBlob(RequestSkinBlob payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            byte[] bytes = SkinStore.get().load(payload.hash());
            if (bytes == null) {
                PlayerDisguise.LOGGER.warn("Client {} requested skin {} but server has no copy",
                        sp.getGameProfile().getName(), payload.hash());
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
