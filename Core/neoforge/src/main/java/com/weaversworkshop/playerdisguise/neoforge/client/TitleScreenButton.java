package com.weaversworkshop.playerdisguise.neoforge.client;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import com.weaversworkshop.playerdisguise.client.gui.PseudonymEditScreen;
import net.minecraft.client.Minecraft;
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
        Button btn = Button.builder(
                Component.literal("Pseudonym…"),
                b -> Minecraft.getInstance().setScreen(new PseudonymEditScreen(ts))
        ).bounds(ts.width - 105, 5, 100, 20).build();
        event.addListener(btn);
    }
}
