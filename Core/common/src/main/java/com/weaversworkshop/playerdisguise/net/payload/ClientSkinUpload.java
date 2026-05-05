package com.weaversworkshop.playerdisguise.net.payload;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClientSkinUpload(String hash, byte[] bytes) implements CustomPacketPayload {

    public static final int MAX_BYTES = 64 * 1024;

    public static final Type<ClientSkinUpload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PlayerDisguise.MODID, "client_skin_upload"));

    public static final StreamCodec<FriendlyByteBuf, ClientSkinUpload> STREAM_CODEC =
            CustomPacketPayload.codec(
                    (msg, buf) -> {
                        buf.writeUtf(msg.hash == null ? "" : msg.hash, 128);
                        buf.writeByteArray(msg.bytes == null ? new byte[0] : msg.bytes);
                    },
                    buf -> new ClientSkinUpload(buf.readUtf(128), buf.readByteArray(MAX_BYTES))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
