package com.weaversworkshop.playerdisguise.mixin.client;

import com.weaversworkshop.playerdisguise.client.ClientAliasMirror;
import com.weaversworkshop.playerdisguise.client.ClientDisguiseRegistry;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
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

    @Inject(method = "getTabListDisplayName", at = @At("HEAD"), cancellable = true)
    private void playerdisguise$getTabListDisplayName(CallbackInfoReturnable<Component> cir) {
        PlayerInfo self = (PlayerInfo) (Object) this;
        String alias = ClientAliasMirror.aliasOf(self.getProfile().getId());
        if (alias != null) cir.setReturnValue(Component.literal(alias));
    }
}
