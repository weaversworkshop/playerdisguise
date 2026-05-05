package com.weaversworkshop.playerdisguise.net.payload;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ProceedWithoutSkin() implements CustomPacketPayload {
    public static final ProceedWithoutSkin INSTANCE = new ProceedWithoutSkin();

    public static final Type<ProceedWithoutSkin> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PlayerDisguise.MODID, "proceed_without_skin"));

    public static final StreamCodec<FriendlyByteBuf, ProceedWithoutSkin> STREAM_CODEC =
            CustomPacketPayload.codec(
                    (msg, buf) -> { /* empty */ },
                    buf -> INSTANCE
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
