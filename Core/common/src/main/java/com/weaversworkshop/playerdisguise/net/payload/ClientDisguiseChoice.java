package com.weaversworkshop.playerdisguise.net.payload;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClientDisguiseChoice(String pseudonym) implements CustomPacketPayload {
    public static final Type<ClientDisguiseChoice> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PlayerDisguise.MODID, "client_disguise_choice"));

    public static final StreamCodec<FriendlyByteBuf, ClientDisguiseChoice> STREAM_CODEC =
            CustomPacketPayload.codec(
                    (msg, buf) -> buf.writeUtf(msg.pseudonym(), 32),
                    buf -> new ClientDisguiseChoice(buf.readUtf(32))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
