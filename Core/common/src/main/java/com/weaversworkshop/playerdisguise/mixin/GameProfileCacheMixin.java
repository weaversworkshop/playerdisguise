package com.weaversworkshop.playerdisguise.mixin;

import com.mojang.authlib.GameProfile;
import com.weaversworkshop.playerdisguise.server.AliasRegistry;
import net.minecraft.server.players.GameProfileCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.UUID;

@Mixin(GameProfileCache.class)
public abstract class GameProfileCacheMixin {

    @Inject(method = "get(Ljava/util/UUID;)Ljava/util/Optional;", at = @At("RETURN"), cancellable = true)
    private void playerdisguise$getByUuid(UUID uuid, CallbackInfoReturnable<Optional<GameProfile>> cir) {
        String alias = AliasRegistry.get().aliasOf(uuid);
        if (alias == null) return;
        cir.setReturnValue(Optional.of(new GameProfile(uuid, alias)));
    }
}
