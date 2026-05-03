package com.weaversworkshop.playerdisguise.config;

import org.jetbrains.annotations.Nullable;

public record PseudonymConfig(
        @Nullable String pseudonymName,
        @Nullable String skinFileName,
        @Nullable String skinHash
) {
    public static final PseudonymConfig EMPTY = new PseudonymConfig(null, null, null);

    public boolean hasPseudonym() {
        return pseudonymName != null && !pseudonymName.isBlank();
    }

    public boolean hasSkin() {
        return skinFileName != null && !skinFileName.isBlank();
    }
}
