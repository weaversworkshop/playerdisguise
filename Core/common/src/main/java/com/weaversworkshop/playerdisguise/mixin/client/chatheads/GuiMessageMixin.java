package com.weaversworkshop.playerdisguise.mixin.client.chatheads;

import com.weaversworkshop.playerdisguise.client.chatheads.SkinSnapshotHolder;
import net.minecraft.client.GuiMessage;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(GuiMessage.class)
public abstract class GuiMessageMixin implements SkinSnapshotHolder {
    @Unique private ResourceLocation playerdisguise$skinSnapshot;

    @Override
    public ResourceLocation playerdisguise$getSkinSnapshot() {
        return this.playerdisguise$skinSnapshot;
    }

    @Override
    public void playerdisguise$setSkinSnapshot(ResourceLocation snap) {
        this.playerdisguise$skinSnapshot = snap;
    }
}
