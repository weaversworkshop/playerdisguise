package com.weaversworkshop.playerdisguise.mixin.client;

import com.mojang.authlib.properties.Property;
import com.weaversworkshop.playerdisguise.client.skin.DisguisedSkullTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.SkullBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

@Mixin(SkullBlockRenderer.class)
public class SkullBlockRendererMixin {
    private static final String DISGUISE_PROPERTY = "playerdisguise_skin";

    @Inject(method = "getRenderType(Lnet/minecraft/world/level/block/SkullBlock$Type;Lnet/minecraft/world/item/component/ResolvableProfile;)Lnet/minecraft/client/renderer/RenderType;",
            at = @At("HEAD"), cancellable = true)
    private static void playerdisguise$getRenderType(SkullBlock.Type type, ResolvableProfile profile,
                                                     CallbackInfoReturnable<RenderType> cir) {
        if (profile == null || profile.gameProfile() == null) return;
        Collection<Property> props = profile.gameProfile().getProperties().get(DISGUISE_PROPERTY);
        if (props.isEmpty()) return;
        String value = props.iterator().next().value();
        int sep = value.indexOf(':');
        String hash = sep < 0 ? value : value.substring(0, sep);
        String model = sep < 0 ? "wide" : value.substring(sep + 1);

        ResourceLocation rl = DisguisedSkullTexture.resolve(hash, model);
        if (rl == null) return;
        cir.setReturnValue(RenderType.entityTranslucent(rl));
    }
}
