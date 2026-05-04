package com.weaversworkshop.playerdisguise.mixin.client.chatheads;

import com.weaversworkshop.playerdisguise.client.chatheads.ChatHeadSnapshotState;
import com.weaversworkshop.playerdisguise.client.chatheads.SkinSnapshotHolder;
import dzwdz.chat_heads.ChatHeads;
import dzwdz.chat_heads.HeadData;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {

    @Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"))
    private void playerdisguise$pushLineSource(GuiMessage msg, CallbackInfo ci) {
        SkinSnapshotHolder holder = (SkinSnapshotHolder) (Object) msg;
        ResourceLocation s = holder.playerdisguise$getSkinSnapshot();
        if (s == null) {
            // Read chat_heads' final sender (set by handleAddedMessage including its heuristic
            // detection). Snapshot the live disguise skin's texture; from now on this message
            // is locked to it regardless of future skin/alias changes.
            HeadData hd = ChatHeads.lastSenderData;
            if (hd != null) {
                PlayerInfo info = hd.playerInfo();
                if (info != null) {
                    PlayerSkin skin = info.getSkin();
                    if (skin != null) {
                        s = skin.texture();
                        holder.playerdisguise$setSkinSnapshot(s);
                    }
                }
            }
        }
        if (s != null) ChatHeadSnapshotState.LINE_SRC.set(s);
    }

    @Inject(method = "addMessageToDisplayQueue", at = @At("RETURN"))
    private void playerdisguise$popLineSource(GuiMessage msg, CallbackInfo ci) {
        ChatHeadSnapshotState.LINE_SRC.remove();
    }
}
