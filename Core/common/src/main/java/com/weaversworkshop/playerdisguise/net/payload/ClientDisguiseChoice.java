package com.weaversworkshop.playerdisguise.net.payload;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClientDisguiseChoice(String pseudonym, String skinHash, String skinModel, byte[] skinBytes)
        implements CustomPacketPayload {

    public static final int MAX_BYTES = 64 * 1024;

    public static final Type<ClientDisguiseChoice> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PlayerDisguise.MODID, "client_disguise_choice"));

    public static final StreamCodec<FriendlyByteBuf, ClientDisguiseChoice> STREAM_CODEC =
            CustomPacketPayload.codec(
                    (msg, buf) -> {
                        buf.writeUtf(msg.pseudonym, 32);
                        buf.writeUtf(msg.skinHash == null ? "" : msg.skinHash, 128);
                        buf.writeUtf(msg.skinModel == null ? "" : msg.skinModel, 16);
                        buf.writeByteArray(msg.skinBytes == null ? new byte[0] : msg.skinBytes);
                    },
                    buf -> new ClientDisguiseChoice(
                            buf.readUtf(32),
                            buf.readUtf(128),
                            buf.readUtf(16),
                            buf.readByteArray(MAX_BYTES)
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
