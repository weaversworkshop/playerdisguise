package com.weaversworkshop.playerdisguise.net.payload;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ServerNeedsSkinUpload(String hash) implements CustomPacketPayload {

    public static final Type<ServerNeedsSkinUpload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PlayerDisguise.MODID, "server_needs_skin_upload"));

    public static final StreamCodec<FriendlyByteBuf, ServerNeedsSkinUpload> STREAM_CODEC =
            CustomPacketPayload.codec(
                    (msg, buf) -> buf.writeUtf(msg.hash == null ? "" : msg.hash, 128),
                    buf -> new ServerNeedsSkinUpload(buf.readUtf(128))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
