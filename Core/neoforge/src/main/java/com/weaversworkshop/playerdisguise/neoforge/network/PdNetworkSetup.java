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
import com.weaversworkshop.playerdisguise.net.payload.ClientSkinUpload;
import com.weaversworkshop.playerdisguise.net.payload.PlayerDisguiseUpdate;
import com.weaversworkshop.playerdisguise.net.payload.ProceedWithoutSkin;
import com.weaversworkshop.playerdisguise.net.payload.RequestSkinBlob;
import com.weaversworkshop.playerdisguise.net.payload.ServerNeedsSkinUpload;
import com.weaversworkshop.playerdisguise.net.payload.ServerRequestDisguise;
import com.weaversworkshop.playerdisguise.net.payload.SkinBlob;
import com.weaversworkshop.playerdisguise.net.payload.SkinUploadFailed;
import com.weaversworkshop.playerdisguise.server.AliasClaimTask;
import com.weaversworkshop.playerdisguise.server.AliasContentFilter;
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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = PlayerDisguise.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class PdNetworkSetup {
    private PdNetworkSetup() {}

    /**
     * Per-listener handshake state for the two-step skin protocol. Holds the alias the player wants to
     * claim plus the hash they declared, so when {@link ClientSkinUpload} or {@link ProceedWithoutSkin}
     * arrives we can validate it against this listener's expected state and commit (or not).
     */
    private record Pending(UUID uuid, String alias, String hash, String model) {}

    private static final Map<ServerConfigurationPacketListenerImpl, Pending> PENDING = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();

        // Configuration phase — alias handshake
        registrar.configurationToClient(
                ServerRequestDisguise.TYPE, ServerRequestDisguise.STREAM_CODEC, PdNetworkSetup::handleRequestOnClient);
        registrar.configurationToServer(
                ClientDisguiseChoice.TYPE, ClientDisguiseChoice.STREAM_CODEC, PdNetworkSetup::handleChoiceConfig);
        registrar.configurationToClient(
                ServerNeedsSkinUpload.TYPE, ServerNeedsSkinUpload.STREAM_CODEC, PdNetworkSetup::handleNeedsUploadOnClient);
        registrar.configurationToServer(
                ClientSkinUpload.TYPE, ClientSkinUpload.STREAM_CODEC, PdNetworkSetup::handleSkinUploadConfig);
        registrar.configurationToClient(
                SkinUploadFailed.TYPE, SkinUploadFailed.STREAM_CODEC, PdNetworkSetup::handleUploadFailedOnClient);
        registrar.configurationToServer(
                ProceedWithoutSkin.TYPE, ProceedWithoutSkin.STREAM_CODEC, PdNetworkSetup::handleProceedWithoutSkinConfig);

        // Play phase — disguise broadcasts and skin blob exchange
        registrar.playToClient(
                PlayerDisguiseUpdate.TYPE, PlayerDisguiseUpdate.STREAM_CODEC, PdNetworkSetup::handleUpdateOnClient);
        registrar.playToClient(
                SkinBlob.TYPE, SkinBlob.STREAM_CODEC, PdNetworkSetup::handleBlobOnClient);
        registrar.playToServer(
                RequestSkinBlob.TYPE, RequestSkinBlob.STREAM_CODEC, PdNetworkSetup::handleClientRequestBlob);
    }

    public static void broadcastUpdate(MinecraftServer server, UUID uuid, String alias, String hash, String model) {
        PlayerDisguiseUpdate payload = new PlayerDisguiseUpdate(
                uuid,
                alias == null ? "" : alias,
                hash == null ? "" : hash,
                model == null ? "" : model);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(p, payload);
        }
    }

    public static void sendBulkSnapshotTo(ServerPlayer recipient) {
        MinecraftServer server = recipient.server;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String alias = AliasRegistry.get().aliasOf(p.getUUID());
            if (alias == null) continue;
            ServerDisguiseState.Skin sk = ServerDisguiseState.get().skinFor(p.getUUID());
            String hash = sk == null ? "" : sk.hash();
            String model = sk == null ? "" : sk.model();
            PacketDistributor.sendToPlayer(recipient, new PlayerDisguiseUpdate(p.getUUID(), alias, hash, model));
        }
    }

    // ---- Server: receives the client's claim choice (no bytes) ----
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

        String requested = payload.alias();
        // No alias requested: clear any prior state and finish.
        if (requested == null || requested.isBlank()) {
            AliasRegistry.get().release(uuid);
            ServerDisguiseState.get().clearSkin(uuid);
            PENDING.remove(listener);
            context.finishCurrentTask(AliasClaimTask.TYPE);
            return;
        }

        if (AliasContentFilter.get().isBlocked(requested)) {
            PlayerDisguise.LOGGER.info("Rejected alias '{}' from {} — matched alias blocklist", requested, realName);
            context.disconnect(Component.literal(
                    "Alias '" + requested + "' is not allowed on this server. Pick a different one and rejoin."));
            return;
        }

        AliasRegistry.ClaimResult result = AliasRegistry.get().claim(uuid, requested);
        switch (result) {
            case ACCEPTED, RESUMED_FROM_COOLDOWN -> {
                String hash = payload.skinHash() == null ? "" : payload.skinHash();
                String model = payload.skinModel() == null ? "" : payload.skinModel();
                if (hash.isBlank()) {
                    // Alias-only claim — no skin involved.
                    AliasRegistry.get().clearActiveSkin(uuid);
                    ServerDisguiseState.get().clearSkin(uuid);
                    PENDING.remove(listener);
                    PlayerDisguise.LOGGER.info("Player {} claimed alias '{}' ({}) skin=<none>", realName, requested, result);
                    context.finishCurrentTask(AliasClaimTask.TYPE);
                } else if (SkinStore.get().has(hash)) {
                    // Cache hit — server already has the bytes. Commit immediately.
                    AliasRegistry.get().setActiveSkin(uuid, hash, model);
                    ServerDisguiseState.get().putSkin(uuid, hash, model);
                    PENDING.remove(listener);
                    PlayerDisguise.LOGGER.info("Player {} claimed alias '{}' ({}) skin={} (cache hit)", realName, requested, result, hash);
                    context.finishCurrentTask(AliasClaimTask.TYPE);
                } else {
                    // Cache miss — ask client to upload. Stash the expected hash so we can validate it later.
                    PENDING.put(listener, new Pending(uuid, requested, hash, model));
                    context.reply(new ServerNeedsSkinUpload(hash));
                }
            }
            case REJECTED_ACTIVE_OTHER -> context.disconnect(Component.literal(
                    "Alias '" + requested + "' is in use by another player. Pick a different one and rejoin."));
            case REJECTED_COOLDOWN_OTHER -> context.disconnect(Component.literal(
                    "Alias '" + requested + "' is on cooldown for another player. Pick a different one and rejoin."));
            case REJECTED_REAL_NAME -> context.disconnect(Component.literal(
                    "Alias '" + requested + "' is reserved as another player's real name."));
            case INVALID -> context.disconnect(Component.literal("Invalid alias."));
        }
    }

    // ---- Server: receives the requested skin upload ----
    private static void handleSkinUploadConfig(ClientSkinUpload payload, IPayloadContext context) {
        if (!(context.listener() instanceof ServerConfigurationPacketListenerImpl listener)) {
            context.disconnect(Component.literal("Skin upload received outside configuration phase"));
            return;
        }
        Pending pending = PENDING.get(listener);
        if (pending == null) {
            // Unsolicited upload — anti-spam guard. Disconnect.
            PlayerDisguise.LOGGER.warn("Rejecting unsolicited ClientSkinUpload from {}", listener.playerProfile() == null ? "?" : listener.playerProfile().getName());
            context.disconnect(Component.literal("Unexpected skin upload."));
            return;
        }
        if (!pending.hash.equals(payload.hash())) {
            PlayerDisguise.LOGGER.warn("Skin upload hash mismatch: expected {} got {}", pending.hash, payload.hash());
            context.reply(new SkinUploadFailed("Hash mismatch in upload"));
            return;
        }
        boolean stored = SkinStore.get().store(pending.hash, payload.bytes());
        if (!stored) {
            context.reply(new SkinUploadFailed("Skin failed validation"));
            return;
        }
        // Validation OK — commit.
        AliasRegistry.get().setActiveSkin(pending.uuid, pending.hash, pending.model);
        ServerDisguiseState.get().putSkin(pending.uuid, pending.hash, pending.model);
        PENDING.remove(listener);
        SkinStore.get().enforceCap();
        String name = listener.playerProfile() == null ? "?" : listener.playerProfile().getName();
        PlayerDisguise.LOGGER.info("Player {} claimed alias '{}' skin={} (uploaded)", name, pending.alias, pending.hash);
        context.finishCurrentTask(AliasClaimTask.TYPE);
    }

    // ---- Server: client confirmed they want to join exposed (alias only, real skin) ----
    private static void handleProceedWithoutSkinConfig(ProceedWithoutSkin payload, IPayloadContext context) {
        if (!(context.listener() instanceof ServerConfigurationPacketListenerImpl listener)) return;
        Pending pending = PENDING.remove(listener);
        if (pending == null) return; // no-op if not awaiting
        AliasRegistry.get().clearActiveSkin(pending.uuid);
        ServerDisguiseState.get().clearSkin(pending.uuid);
        String name = listener.playerProfile() == null ? "?" : listener.playerProfile().getName();
        PlayerDisguise.LOGGER.info("Player {} claimed alias '{}' skin=<exposed by user choice>", name, pending.alias);
        context.finishCurrentTask(AliasClaimTask.TYPE);
    }

    // ---- Client: received from server during config phase ----
    private static void handleRequestOnClient(ServerRequestDisguise payload, IPayloadContext context) {
        ConfigStore store = PlayerDisguiseClient.config();
        ProfileBook book = store.book();
        String name = "";
        String hash = "";
        String model = "";
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
                    }
                } catch (Exception e) {
                    PlayerDisguise.LOGGER.warn("Failed to read skin metadata for handshake", e);
                }
            }
        }
        context.reply(new ClientDisguiseChoice(name, hash, model));
    }

    private static void handleNeedsUploadOnClient(ServerNeedsSkinUpload payload, IPayloadContext context) {
        ClientDisguiseHandler.respondToServerSkinRequest(payload.hash(), context::reply);
    }

    private static void handleUploadFailedOnClient(SkinUploadFailed payload, IPayloadContext context) {
        ClientDisguiseHandler.showSkinUploadFailedScreen(payload.reason(), context::reply, context::disconnect);
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
            if (payload.alias() == null || payload.alias().isBlank()) {
                ClientDisguiseHandler.applyClear(payload.uuid());
            } else {
                ClientDisguiseHandler.applyDisguise(payload.uuid(), payload.alias(), payload.skinHash(), payload.skinModel());
            }
        });
    }

    private static void handleBlobOnClient(SkinBlob payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientDisguiseHandler.onBlobReceived(payload.hash(), payload.bytes()));
    }
}
