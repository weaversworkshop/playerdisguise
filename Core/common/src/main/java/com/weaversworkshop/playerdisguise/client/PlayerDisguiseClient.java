package com.weaversworkshop.playerdisguise.client;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import com.weaversworkshop.playerdisguise.config.ConfigStore;

import java.nio.file.Path;

public final class PlayerDisguiseClient {
    private static ConfigStore CONFIG;

    private PlayerDisguiseClient() {}

    public static void init(Path modConfigDir) {
        CONFIG = new ConfigStore(modConfigDir);
        CONFIG.load();
        com.weaversworkshop.playerdisguise.client.skin.ClientSkinCache.get()
                .setDir(modConfigDir.resolve("cache"));
        PlayerDisguise.LOGGER.info("PlayerDisguise client config loaded from {}", modConfigDir);
    }

    public static ConfigStore config() {
        if (CONFIG == null) throw new IllegalStateException("PlayerDisguiseClient not initialized");
        return CONFIG;
    }
}
