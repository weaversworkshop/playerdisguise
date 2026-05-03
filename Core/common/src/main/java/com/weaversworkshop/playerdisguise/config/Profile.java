package com.weaversworkshop.playerdisguise.config;

import net.minecraft.client.resources.PlayerSkin;
import org.jetbrains.annotations.Nullable;

public record Profile(
        String name,
        @Nullable String skinFileName,
        @Nullable String skinHash,
        @Nullable String skinModel
) {
    public boolean hasSkin() {
        return skinFileName != null && !skinFileName.isBlank();
    }

    public PlayerSkin.Model resolvedModel() {
        return PlayerSkin.Model.byName(skinModel);
    }
}
