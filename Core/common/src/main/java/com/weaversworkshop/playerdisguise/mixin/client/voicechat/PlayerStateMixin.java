package com.weaversworkshop.playerdisguise.mixin.client.voicechat;

import com.weaversworkshop.playerdisguise.client.ClientAliasMirror;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(targets = "de.maxhenkel.voicechat.voice.common.PlayerState", remap = false)
public abstract class PlayerStateMixin {

    @Shadow private UUID uuid;

    @Inject(method = "getName", at = @At("HEAD"), cancellable = true, require = 0)
    private void playerdisguise$getName(CallbackInfoReturnable<String> cir) {
        if (uuid == null) return;
        String alias = ClientAliasMirror.aliasOf(uuid);
        if (alias != null) cir.setReturnValue(alias);
    }
}
