package com.weaversworkshop.playerdisguise.fabric;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import net.fabricmc.api.ClientModInitializer;

public class PlayerDisguiseClientFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PlayerDisguise.LOGGER.info("PlayerDisguise fabric client init");
    }
}
