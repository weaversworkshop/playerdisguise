package com.weaversworkshop.playerdisguise.mixin;

import com.weaversworkshop.playerdisguise.server.AliasRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.UUID;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {

    @Inject(method = "getPlayerByName", at = @At("RETURN"), cancellable = true)
    private void playerdisguise$lookupByPseudonym(String username, CallbackInfoReturnable<ServerPlayer> cir) {
        if (cir.getReturnValue() != null) return;
        UUID uuid = AliasRegistry.get().uuidOf(username);
        if (uuid == null) return;
        PlayerList self = (PlayerList) (Object) this;
        ServerPlayer p = self.getPlayer(uuid);
        if (p != null) cir.setReturnValue(p);
    }

    @Inject(method = "getPlayerNamesArray", at = @At("RETURN"), cancellable = true)
    private void playerdisguise$replaceWithPseudonyms(CallbackInfoReturnable<String[]> cir) {
        PlayerList self = (PlayerList) (Object) this;
        List<ServerPlayer> players = self.getPlayers();
        String[] out = new String[players.size()];
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer sp = players.get(i);
            String alias = AliasRegistry.get().pseudonymOf(sp.getUUID());
            out[i] = alias != null ? alias : sp.getGameProfile().getName();
        }
        cir.setReturnValue(out);
    }
}
