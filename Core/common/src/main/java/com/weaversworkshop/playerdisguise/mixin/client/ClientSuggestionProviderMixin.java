package com.weaversworkshop.playerdisguise.mixin.client;

import com.weaversworkshop.playerdisguise.server.ServerDisguiseRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Mixin(ClientSuggestionProvider.class)
public abstract class ClientSuggestionProviderMixin {
    @Inject(method = "getOnlinePlayerNames", at = @At("RETURN"), cancellable = true)
    private void playerdisguise$replaceWithPseudonyms(CallbackInfoReturnable<Collection<String>> cir) {
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn == null) return;
        List<String> out = new ArrayList<>();
        for (PlayerInfo info : conn.getOnlinePlayers()) {
            String alias = ServerDisguiseRegistry.pseudonymOf(info.getProfile().getId());
            out.add(alias != null ? alias : info.getProfile().getName());
        }
        cir.setReturnValue(out);
    }
}
