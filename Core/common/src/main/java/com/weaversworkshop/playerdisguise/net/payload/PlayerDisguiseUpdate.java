package com.weaversworkshop.playerdisguise.net.payload;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record PlayerDisguiseUpdate(UUID uuid, String pseudonym, String skinHash, String skinModel)
        implements CustomPacketPayload {
    public static final Type<PlayerDisguiseUpdate> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PlayerDisguise.MODID, "player_disguise_update"));

    public static final StreamCodec<FriendlyByteBuf, PlayerDisguiseUpdate> STREAM_CODEC =
            CustomPacketPayload.codec(
                    (msg, buf) -> {
                        buf.writeUUID(msg.uuid);
                        buf.writeUtf(msg.pseudonym == null ? "" : msg.pseudonym, 32);
                        buf.writeUtf(msg.skinHash == null ? "" : msg.skinHash, 128);
                        buf.writeUtf(msg.skinModel == null ? "" : msg.skinModel, 16);
                    },
                    buf -> new PlayerDisguiseUpdate(buf.readUUID(), buf.readUtf(32), buf.readUtf(128), buf.readUtf(16))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
