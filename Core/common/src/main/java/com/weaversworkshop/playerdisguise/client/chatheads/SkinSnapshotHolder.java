package com.weaversworkshop.playerdisguise.client.chatheads;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public interface SkinSnapshotHolder {
    @Nullable ResourceLocation playerdisguise$getSkinSnapshot();
    void playerdisguise$setSkinSnapshot(@Nullable ResourceLocation snap);
}
