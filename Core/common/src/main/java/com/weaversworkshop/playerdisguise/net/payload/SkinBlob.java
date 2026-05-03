package com.weaversworkshop.playerdisguise.net.payload;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SkinBlob(String hash, byte[] bytes) implements CustomPacketPayload {
    public static final int MAX_BYTES = 64 * 1024;

    public static final Type<SkinBlob> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PlayerDisguise.MODID, "skin_blob"));

    public static final StreamCodec<FriendlyByteBuf, SkinBlob> STREAM_CODEC =
            CustomPacketPayload.codec(
                    (msg, buf) -> { buf.writeUtf(msg.hash, 128); buf.writeByteArray(msg.bytes); },
                    buf -> new SkinBlob(buf.readUtf(128), buf.readByteArray(MAX_BYTES))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
