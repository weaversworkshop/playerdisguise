package com.weaversworkshop.playerdisguise.net.payload;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestSkinBlob(String hash) implements CustomPacketPayload {
    public static final Type<RequestSkinBlob> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PlayerDisguise.MODID, "request_skin_blob"));

    public static final StreamCodec<FriendlyByteBuf, RequestSkinBlob> STREAM_CODEC =
            CustomPacketPayload.codec(
                    (msg, buf) -> buf.writeUtf(msg.hash, 128),
                    buf -> new RequestSkinBlob(buf.readUtf(128))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
