package com.weaversworkshop.playerdisguise.config;

import org.jetbrains.annotations.Nullable;

public record Profile(String name, @Nullable String skinFileName, @Nullable String skinHash) {
    public boolean hasSkin() {
        return skinFileName != null && !skinFileName.isBlank();
    }
}
