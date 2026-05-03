package com.weaversworkshop.playerdisguise.client.gui;

import com.mojang.authlib.GameProfile;
import com.weaversworkshop.playerdisguise.PlayerDisguise;
import com.weaversworkshop.playerdisguise.client.PlayerDisguiseClient;
import com.weaversworkshop.playerdisguise.client.skin.SkinLibrary;
import com.weaversworkshop.playerdisguise.client.skin.SkinTextureCache;
import com.weaversworkshop.playerdisguise.config.ConfigStore;
import com.weaversworkshop.playerdisguise.config.Profile;
import com.weaversworkshop.playerdisguise.config.ProfileBook;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PseudonymCarouselScreen extends Screen {
    private static final Component TITLE = Component.literal("Pseudonyms");

    private final @Nullable Screen parent;
    private int viewIndex;

    private PlayerSkinWidget skinWidget;
    private Button leftBtn, rightBtn, selectBtn, editBtn, deleteBtn, newBtn, doneBtn;

    private final Map<String, PlayerSkin> resolvedSkins = new HashMap<>();
    private @Nullable PlayerSkin realSkin;
    private final PlayerSkin fallbackSkin;
    private final String realName;

    public PseudonymCarouselScreen(@Nullable Screen parent) {
        super(TITLE);
        this.parent = parent;
        Minecraft mc = Minecraft.getInstance();
        User user = mc.getUser();
        UUID uid = user.getProfileId() != null ? user.getProfileId() : UUID.nameUUIDFromBytes(user.getName().getBytes());
        this.realName = user.getName();
        this.fallbackSkin = DefaultPlayerSkin.get(uid);
        PlayerDisguiseClient.config().load();
        this.viewIndex = PlayerDisguiseClient.config().book().activeIndex();

        GameProfile gp = mc.getGameProfile();
        mc.getSkinManager().getOrLoad(gp).thenAccept(s -> realSkin = s);
    }

    @Override
    protected void init() {
        ProfileBook book = PlayerDisguiseClient.config().book();
        if (viewIndex >= book.totalSlots()) viewIndex = 0;

        int cx = this.width / 2;
        int cy = this.height / 2;

        skinWidget = new PlayerSkinWidget(80, 110, this.minecraft.getEntityModels(), this::currentSkin);
        skinWidget.setX(cx - 40);
        skinWidget.setY(cy - 70);
        addRenderableWidget(skinWidget);

        leftBtn = Button.builder(Component.literal("<"), b -> cycle(-1))
                .bounds(cx - 100, cy - 25, 20, 40).build();
        rightBtn = Button.builder(Component.literal(">"), b -> cycle(1))
                .bounds(cx + 80, cy - 25, 20, 40).build();
        addRenderableWidget(leftBtn);
        addRenderableWidget(rightBtn);

        int rowY = cy + 60;
        selectBtn = Button.builder(Component.literal("Select"), b -> select())
                .bounds(cx - 120, rowY, 75, 20).build();
        editBtn = Button.builder(Component.literal("Edit"), b -> edit())
                .bounds(cx - 38, rowY, 75, 20).build();
        deleteBtn = Button.builder(Component.literal("Delete"), b -> delete())
                .bounds(cx + 45, rowY, 75, 20).build();
        addRenderableWidget(selectBtn);
        addRenderableWidget(editBtn);
        addRenderableWidget(deleteBtn);

        int btnY = this.height - 28;
        newBtn = Button.builder(Component.literal("+ New Profile"), b -> newProfile())
                .bounds(cx - 110, btnY, 110, 20).build();
        doneBtn = Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(cx + 5, btnY, 100, 20).build();
        addRenderableWidget(newBtn);
        addRenderableWidget(doneBtn);

        updateButtons();
    }

    private PlayerSkin currentSkin() {
        if (viewIndex == 0) return realSkin != null ? realSkin : fallbackSkin;
        Profile p = PlayerDisguiseClient.config().book().profiles().get(viewIndex - 1);
        if (!p.hasSkin()) return fallbackSkin;
        PlayerSkin cached = resolvedSkins.get(p.skinFileName());
        if (cached != null) return cached;
        try {
            SkinLibrary lib = new SkinLibrary(PlayerDisguiseClient.config().skinsDir());
            lib.refresh();
            SkinLibrary.Entry.Valid v = lib.findByFilename(p.skinFileName());
            if (v != null) {
                PlayerSkin s = SkinTextureCache.getOrRegister(v.bytes(), v.sha256(), p.resolvedModel());
                resolvedSkins.put(p.skinFileName(), s);
                return s;
            }
        } catch (Exception e) {
            PlayerDisguise.LOGGER.warn("Failed to load skin for profile {}", p.name(), e);
        }
        return SkinTextureCache.missing();
    }

    private boolean isCurrentSkinMissing() {
        if (viewIndex == 0) return false;
        Profile p = PlayerDisguiseClient.config().book().profiles().get(viewIndex - 1);
        if (!p.hasSkin()) return false;
        SkinLibrary lib = new SkinLibrary(PlayerDisguiseClient.config().skinsDir());
        lib.refresh();
        return lib.findByFilename(p.skinFileName()) == null;
    }

    private String currentName() {
        if (viewIndex == 0) return realName;
        return PlayerDisguiseClient.config().book().profiles().get(viewIndex - 1).name();
    }

    private void cycle(int delta) {
        int total = PlayerDisguiseClient.config().book().totalSlots();
        viewIndex = ((viewIndex + delta) % total + total) % total;
        updateButtons();
    }

    private void updateButtons() {
        ProfileBook book = PlayerDisguiseClient.config().book();
        boolean isReal = viewIndex == 0;
        boolean isActive = viewIndex == book.activeIndex();
        boolean missing = isCurrentSkinMissing();
        selectBtn.active = !isActive && !missing;
        if (missing) {
            Profile p = book.profiles().get(viewIndex - 1);
            selectBtn.setTooltip(Tooltip.create(Component.literal("Skin file '" + p.skinFileName() + "' is missing")));
        } else {
            selectBtn.setTooltip(null);
        }
        editBtn.visible = !isReal;
        deleteBtn.visible = !isReal;
        int cx = this.width / 2;
        selectBtn.setX(isReal ? cx - selectBtn.getWidth() / 2 : cx - 120);
        leftBtn.active = book.totalSlots() > 1;
        rightBtn.active = book.totalSlots() > 1;
    }

    private void select() {
        ConfigStore store = PlayerDisguiseClient.config();
        try {
            store.save(store.book().withActiveIndex(viewIndex));
            updateButtons();
        } catch (Exception e) {
            PlayerDisguise.LOGGER.warn("Failed to save active index", e);
        }
    }

    private void edit() {
        if (viewIndex == 0) return;
        int storedIndex = viewIndex - 1;
        Profile current = PlayerDisguiseClient.config().book().profiles().get(storedIndex);
        Minecraft.getInstance().setScreen(new ProfileEditScreen(this, current, updated -> {
            ConfigStore store = PlayerDisguiseClient.config();
            try {
                store.save(store.book().replaceProfile(storedIndex, updated));
                resolvedSkins.remove(current.skinFileName());
            } catch (Exception e) {
                PlayerDisguise.LOGGER.warn("Failed to save edited profile", e);
            }
        }));
    }

    private void delete() {
        if (viewIndex == 0) return;
        ConfigStore store = PlayerDisguiseClient.config();
        try {
            int storedIndex = viewIndex - 1;
            ProfileBook updated = store.book().removeProfile(storedIndex);
            store.save(updated);
            if (viewIndex >= updated.totalSlots()) viewIndex = updated.totalSlots() - 1;
            updateButtons();
        } catch (Exception e) {
            PlayerDisguise.LOGGER.warn("Failed to delete profile", e);
        }
    }

    private void newProfile() {
        Minecraft.getInstance().setScreen(new ProfileEditScreen(this, null, created -> {
            ConfigStore store = PlayerDisguiseClient.config();
            try {
                ProfileBook updated = store.book().addProfile(created);
                store.save(updated);
                viewIndex = updated.activeIndex();
            } catch (Exception e) {
                PlayerDisguise.LOGGER.warn("Failed to save new profile", e);
            }
        }));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFF);

        ProfileBook book = PlayerDisguiseClient.config().book();
        int cy = this.height / 2;
        String name = currentName();
        boolean isActive = viewIndex == book.activeIndex();
        Component nameComp = Component.literal(name)
                .withStyle(isActive ? ChatFormatting.GREEN : ChatFormatting.WHITE);
        g.drawCenteredString(this.font, nameComp, this.width / 2, cy - 90, 0xFFFFFF);

        String pos = (viewIndex + 1) + " / " + book.totalSlots();
        g.drawCenteredString(this.font,
                Component.literal(pos).withStyle(ChatFormatting.GRAY),
                this.width / 2, cy - 78, 0xAAAAAA);

        if (viewIndex == 0) {
            g.drawCenteredString(this.font,
                    Component.literal("(real identity)").withStyle(ChatFormatting.DARK_GRAY),
                    this.width / 2, cy + 50, 0x888888);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
