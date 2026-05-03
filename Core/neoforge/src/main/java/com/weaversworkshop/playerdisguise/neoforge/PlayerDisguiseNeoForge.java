package com.weaversworkshop.playerdisguise.neoforge;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import com.weaversworkshop.playerdisguise.client.PlayerDisguiseClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;

@Mod(PlayerDisguise.MODID)
public class PlayerDisguiseNeoForge {
    public PlayerDisguiseNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        PlayerDisguise.init();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            PlayerDisguiseClient.init(FMLPaths.CONFIGDIR.get().resolve(PlayerDisguise.MODID));
        }
    }
}
