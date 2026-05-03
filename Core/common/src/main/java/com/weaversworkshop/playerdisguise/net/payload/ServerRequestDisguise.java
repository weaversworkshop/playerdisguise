package com.weaversworkshop.playerdisguise.net.payload;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ServerRequestDisguise() implements CustomPacketPayload {
    public static final ServerRequestDisguise INSTANCE = new ServerRequestDisguise();

    public static final Type<ServerRequestDisguise> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PlayerDisguise.MODID, "server_request_disguise"));

    public static final StreamCodec<FriendlyByteBuf, ServerRequestDisguise> STREAM_CODEC =
            CustomPacketPayload.codec(
                    (msg, buf) -> { /* empty */ },
                    buf -> INSTANCE
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
