package com.weaversworkshop.playerdisguise.mixin.client.chatheads;

import com.weaversworkshop.playerdisguise.client.chatheads.ChatHeadSnapshotState;
import com.weaversworkshop.playerdisguise.client.chatheads.SkinSnapshotHolder;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiMessage.Line.class)
public abstract class GuiMessageLineMixin implements SkinSnapshotHolder {
    @Unique private ResourceLocation playerdisguise$skinSnapshot;

    @Override
    public ResourceLocation playerdisguise$getSkinSnapshot() {
        return this.playerdisguise$skinSnapshot;
    }

    @Override
    public void playerdisguise$setSkinSnapshot(ResourceLocation snap) {
        this.playerdisguise$skinSnapshot = snap;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void playerdisguise$initSnapshot(int addedTime, FormattedCharSequence content, GuiMessageTag tag, boolean endOfEntry, CallbackInfo ci) {
        this.playerdisguise$skinSnapshot = ChatHeadSnapshotState.LINE_SRC.get();
    }
}
