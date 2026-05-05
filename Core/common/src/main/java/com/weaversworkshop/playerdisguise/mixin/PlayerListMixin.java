package com.weaversworkshop.playerdisguise.mixin;

import com.mojang.authlib.GameProfile;
import com.weaversworkshop.playerdisguise.server.AliasRegistry;
import com.weaversworkshop.playerdisguise.server.CommandContextHolder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.ServerOpListEntry;
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
        ServerPlayer found = cir.getReturnValue();
        if (found != null) {
            // Vanilla matched by real name. If the player is disguised and the lookup wasn't their alias,
            // hide them from the caller — UNLESS the caller is server console or an op who outranks the target.
            if (AliasRegistry.get().isDisguised(found.getUUID())
                    && !username.equalsIgnoreCase(AliasRegistry.get().pseudonymOf(found.getUUID()))) {
                if (!playerdisguise$callerCanSeeRealName(found)) cir.setReturnValue(null);
            }
            return;
        }
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

    /**
     * True when the current command source is permitted to resolve {@code target}'s real name despite the
     * disguise privacy-block. Server console always passes; an op passes when their op level is &gt;= the
     * target's op level (and at least 1). Non-command callers (no source on the stack) never pass — that
     * preserves the existing privacy guarantee for direct API consumers.
     */
    private static boolean playerdisguise$callerCanSeeRealName(ServerPlayer target) {
        CommandSourceStack source = CommandContextHolder.current();
        if (source == null) return false;
        // Server console / command block / non-entity executor → full access.
        if (source.getEntity() == null) return true;

        MinecraftServer server = source.getServer();
        if (server == null) return false;

        int runnerOp = 0;
        for (int i = 4; i >= 1; i--) {
            if (source.hasPermission(i)) { runnerOp = i; break; }
        }
        if (runnerOp < 1) return false;

        String realName = AliasRegistry.get().realNameOf(target.getUUID());
        GameProfile profile = new GameProfile(target.getUUID(), realName == null ? target.getGameProfile().getName() : realName);
        ServerOpListEntry entry = server.getPlayerList().getOps().get(profile);
        int targetOp = entry == null ? 0 : entry.getLevel();
        return runnerOp >= targetOp;
    }
}
