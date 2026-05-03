package com.weaversworkshop.playerdisguise.mixin;

import com.mojang.authlib.GameProfile;
import com.weaversworkshop.playerdisguise.server.AliasRegistry;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;
import java.util.UUID;

@Mixin(PlayerList.class)
public abstract class PlayerListJoinMessageMixin {

    @Redirect(
        method = "placeNewPlayer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/players/GameProfileCache;get(Ljava/util/UUID;)Ljava/util/Optional;"
        )
    )
    private Optional<GameProfile> playerdisguise$skipRenameDetect(GameProfileCache cache, UUID uuid) {
        if (AliasRegistry.get().isDisguised(uuid)) return Optional.empty();
        return cache.get(uuid);
    }
}
