package com.weaversworkshop.playerdisguise.client.chatheads;

import net.minecraft.resources.ResourceLocation;

public final class ChatHeadSnapshotState {
    private ChatHeadSnapshotState() {}

    public static final ThreadLocal<ResourceLocation> LINE_SRC = new ThreadLocal<>();
    public static final ThreadLocal<ResourceLocation> CURRENT_RENDER = new ThreadLocal<>();
}
