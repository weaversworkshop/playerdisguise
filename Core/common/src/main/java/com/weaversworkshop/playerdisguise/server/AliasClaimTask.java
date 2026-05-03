package com.weaversworkshop.playerdisguise.server;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import com.weaversworkshop.playerdisguise.net.payload.ServerRequestDisguise;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.ConfigurationTask;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;

import java.util.function.Consumer;

public final class AliasClaimTask implements ICustomConfigurationTask {
    public static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type(
            ResourceLocation.fromNamespaceAndPath(PlayerDisguise.MODID, "alias_claim").toString());

    @Override
    public void run(Consumer<CustomPacketPayload> sender) {
        sender.accept(ServerRequestDisguise.INSTANCE);
    }

    @Override
    public ConfigurationTask.Type type() { return TYPE; }
}
