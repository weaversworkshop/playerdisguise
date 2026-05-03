package com.weaversworkshop.playerdisguise.neoforge;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(PlayerDisguise.MODID)
public class PlayerDisguiseNeoForge {
    public PlayerDisguiseNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        PlayerDisguise.init();
    }
}
