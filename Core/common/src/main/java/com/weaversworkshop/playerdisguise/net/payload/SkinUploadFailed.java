package com.weaversworkshop.playerdisguise.net.payload;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SkinUploadFailed(String reason) implements CustomPacketPayload {

    public static final Type<SkinUploadFailed> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PlayerDisguise.MODID, "skin_upload_failed"));

    public static final StreamCodec<FriendlyByteBuf, SkinUploadFailed> STREAM_CODEC =
            CustomPacketPayload.codec(
                    (msg, buf) -> buf.writeUtf(msg.reason == null ? "" : msg.reason, 256),
                    buf -> new SkinUploadFailed(buf.readUtf(256))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
