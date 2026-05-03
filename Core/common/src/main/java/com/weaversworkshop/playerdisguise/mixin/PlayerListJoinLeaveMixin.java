package com.weaversworkshop.playerdisguise.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerList.class)
public abstract class PlayerListJoinLeaveMixin {

    @Redirect(
            method = "placeNewPlayer",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private void playerdisguise$suppressVanillaJoin(PlayerList self, Component msg, boolean overlay) {
        // intentionally noop — we broadcast our own join after the alias is processed
    }
}
