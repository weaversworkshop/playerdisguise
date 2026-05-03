package com.weaversworkshop.playerdisguise.mixin.client;

import com.weaversworkshop.playerdisguise.client.ClientDisguiseRegistry;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInfo.class)
public class PlayerInfoMixin {
    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void playerdisguise$getSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        PlayerInfo self = (PlayerInfo) (Object) this;
        PlayerSkin override = ClientDisguiseRegistry.skin(self.getProfile().getId());
        if (override != null) cir.setReturnValue(override);
    }
}
