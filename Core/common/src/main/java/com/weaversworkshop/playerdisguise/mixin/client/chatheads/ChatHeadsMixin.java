package com.weaversworkshop.playerdisguise.mixin.client.chatheads;

import com.weaversworkshop.playerdisguise.client.chatheads.ChatHeadSnapshotState;
import com.weaversworkshop.playerdisguise.client.chatheads.SkinSnapshotHolder;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dzwdz.chat_heads.ChatHeads", remap = false)
public abstract class ChatHeadsMixin {

    @Inject(
        method = "getHeadData(Lnet/minecraft/client/GuiMessage$Line;)Ldzwdz/chat_heads/HeadData;",
        at = @At("HEAD"),
        require = 1
    )
    private static void playerdisguise$setCurrentRender(GuiMessage.Line line, CallbackInfoReturnable<Object> cir) {
        ResourceLocation s = ((SkinSnapshotHolder) (Object) line).playerdisguise$getSkinSnapshot();
        if (s != null) ChatHeadSnapshotState.CURRENT_RENDER.set(s);
        else ChatHeadSnapshotState.CURRENT_RENDER.remove();
    }

    @Redirect(
        method = "renderChatHead(Lnet/minecraft/client/gui/GuiGraphics;IILnet/minecraft/client/multiplayer/PlayerInfo;FZ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/PlayerSkin;texture()Lnet/minecraft/resources/ResourceLocation;"),
        require = 1
    )
    private static ResourceLocation playerdisguise$swapTexture(PlayerSkin skin) {
        ResourceLocation snap = ChatHeadSnapshotState.CURRENT_RENDER.get();
        return snap != null ? snap : skin.texture();
    }
}
