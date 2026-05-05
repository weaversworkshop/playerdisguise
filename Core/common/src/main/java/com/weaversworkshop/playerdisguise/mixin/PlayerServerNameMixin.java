package com.weaversworkshop.playerdisguise.mixin;

import com.weaversworkshop.playerdisguise.server.AliasRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerServerNameMixin {

    @Inject(method = "getName", at = @At("RETURN"), cancellable = true)
    private void playerdisguise$getName(CallbackInfoReturnable<Component> cir) {
        Player self = (Player) (Object) this;
        String alias = AliasRegistry.get().aliasOf(self.getUUID());
        if (alias != null) cir.setReturnValue(Component.literal(alias));
    }

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void playerdisguise$getDisplayName(CallbackInfoReturnable<Component> cir) {
        Player self = (Player) (Object) this;
        String alias = AliasRegistry.get().aliasOf(self.getUUID());
        if (alias != null) cir.setReturnValue(Component.literal(alias));
    }
}
