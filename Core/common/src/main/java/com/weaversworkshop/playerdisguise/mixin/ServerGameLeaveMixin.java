package com.weaversworkshop.playerdisguise.mixin;

import com.weaversworkshop.playerdisguise.server.PendingJoinTracker;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGameLeaveMixin {

    @Shadow public ServerPlayer player;

    @Redirect(
            method = "removePlayerFromWorld",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private void playerdisguise$suppressLeaveIfPending(PlayerList list, Component msg, boolean overlay) {
        if (PendingJoinTracker.isPending(this.player.getUUID())) return;
        list.broadcastSystemMessage(msg, overlay);
    }
}
