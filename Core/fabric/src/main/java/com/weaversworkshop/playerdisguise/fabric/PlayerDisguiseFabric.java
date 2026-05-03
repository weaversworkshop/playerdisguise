package com.weaversworkshop.playerdisguise.fabric;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import net.fabricmc.api.ModInitializer;

public class PlayerDisguiseFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        PlayerDisguise.init();
    }
}
