package com.weaversworkshop.playerdisguise.neoforge.client;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import com.weaversworkshop.playerdisguise.client.gui.ProfileCarouselScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = PlayerDisguise.MODID, value = Dist.CLIENT)
public final class TitleScreenButton {
    private TitleScreenButton() {}

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen ts)) return;

        int btnX = ts.width / 2 + 104;
        int btnY = ts.height / 4 + 48 + 96;
        for (var child : ts.children()) {
            if (child instanceof AbstractWidget w && "Mods".equalsIgnoreCase(w.getMessage().getString())) {
                btnX = w.getX() + w.getWidth() + 4;
                btnY = w.getY();
                break;
            }
        }

        Button btn = Button.builder(
                Component.translatable("playerdisguise.title.aliases"),
                b -> Minecraft.getInstance().setScreen(new ProfileCarouselScreen(ts))
        ).bounds(btnX, btnY, 70, 20).build();
        event.addListener(btn);
    }
}
