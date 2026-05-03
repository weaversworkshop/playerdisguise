package com.weaversworkshop.playerdisguise.mixin.bluemap;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import com.weaversworkshop.playerdisguise.server.ServerDisguiseState;
import com.weaversworkshop.playerdisguise.server.SkinStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Mixin(targets = "de.bluecolored.bluemap.common.plugin.skins.MojangSkinProvider", remap = false)
public abstract class MojangSkinProviderMixin {

    @Inject(method = "load", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void playerdisguise$load(UUID uuid, CallbackInfoReturnable<Optional<BufferedImage>> cir) throws IOException {
        ServerDisguiseState.Skin skin = ServerDisguiseState.get().skinFor(uuid);
        if (skin == null) return;
        byte[] bytes = SkinStore.get().load(skin.hash());
        if (bytes == null) return;
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
        if (img == null) {
            PlayerDisguise.LOGGER.warn("BlueMap skin proxy: failed to decode disguise PNG hash={} for {}", skin.hash(), uuid);
            return;
        }
        cir.setReturnValue(Optional.of(img));
    }
}
