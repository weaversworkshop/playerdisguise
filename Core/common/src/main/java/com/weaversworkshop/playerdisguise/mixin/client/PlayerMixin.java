package com.weaversworkshop.playerdisguise.mixin.client;

import com.weaversworkshop.playerdisguise.client.ClientDisguiseRegistry;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerMixin {
    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void playerdisguise$getName(CallbackInfoReturnable<Component> cir) {
        Object self = this;
        if (!(self instanceof AbstractClientPlayer)) return;
        Component override = ClientDisguiseRegistry.name(((Player) self).getUUID());
        if (override != null) cir.setReturnValue(override);
    }

    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
    private void playerdisguise$getDisplayName(CallbackInfoReturnable<Component> cir) {
        Object self = this;
        if (!(self instanceof AbstractClientPlayer)) return;
        Component override = ClientDisguiseRegistry.name(((Player) self).getUUID());
        if (override != null) cir.setReturnValue(override);
    }
}
