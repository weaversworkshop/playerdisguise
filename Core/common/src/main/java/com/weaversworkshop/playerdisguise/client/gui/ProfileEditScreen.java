package com.weaversworkshop.playerdisguise.client.gui;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import com.weaversworkshop.playerdisguise.client.PlayerDisguiseClient;
import com.weaversworkshop.playerdisguise.client.skin.SkinLibrary;
import com.weaversworkshop.playerdisguise.config.Profile;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class ProfileEditScreen extends Screen {
    private static final Pattern NAME_RE = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    private final @Nullable Screen parent;
    private final @Nullable Profile initial;
    private final Consumer<Profile> onSave;
    private final SkinLibrary library;

    private EditBox nameField;
    private SkinListWidget list;
    private PlayerSkinWidget skinWidget;
    private Button saveBtn;

    private @Nullable SkinLibrary.Entry.Valid stagedSkinEntry;
    private @Nullable PlayerSkin stagedSkin;
    private final PlayerSkin fallbackSkin = DefaultPlayerSkin.get(UUID.randomUUID());
    private @Nullable String errorMsg;

    public ProfileEditScreen(@Nullable Screen parent, @Nullable Profile initial, Consumer<Profile> onSave) {
        super(Component.literal(initial == null ? "New Profile" : "Edit Profile"));
        this.parent = parent;
        this.initial = initial;
        this.onSave = onSave;
        this.library = new SkinLibrary(PlayerDisguiseClient.config().skinsDir());
    }

    @Override
    protected void init() {
        library.refresh();

        int margin = 12;
        int topY = 36;
        int bottomBarH = 28;
        int listW = this.width / 2 - margin - margin / 2;
        int listH = this.height - topY - bottomBarH - margin;

        list = new SkinListWidget(this.minecraft, listW, listH, topY, 26, this::onSelectEntry);
        list.setX(margin);
        addRenderableWidget(list);

        int rightX = margin + listW + margin;
        int rightW = this.width - rightX - margin;

        nameField = new EditBox(this.font, rightX, topY, rightW, 20, Component.literal("Name"));
        nameField.setMaxLength(16);
        if (initial != null) nameField.setValue(initial.name());
        nameField.setResponder(s -> revalidate());
        addRenderableWidget(nameField);

        skinWidget = new PlayerSkinWidget(80, 110, this.minecraft.getEntityModels(),
                () -> stagedSkin != null ? stagedSkin : fallbackSkin);
        skinWidget.setX(rightX + (rightW - 80) / 2);
        skinWidget.setY(topY + 30);
        addRenderableWidget(skinWidget);

        int btnY = this.height - bottomBarH;
        int gap = 4;
        int avail = this.width - 2 * margin;
        int btnW = Math.max(50, (avail - 3 * gap) / 4);
        int x0 = margin;
        Button openFolder = Button.builder(Component.literal("Open Folder"), b -> openSkinsFolder())
                .bounds(x0, btnY, btnW, 20).build();
        Button refresh = Button.builder(Component.literal("Refresh"), b -> refreshList())
                .bounds(x0 + (btnW + gap), btnY, btnW, 20).build();
        saveBtn = Button.builder(Component.literal("Save"), b -> save())
                .bounds(x0 + 2 * (btnW + gap), btnY, btnW, 20).build();
        Button cancel = Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(x0 + 3 * (btnW + gap), btnY, btnW, 20).build();
        addRenderableWidget(openFolder);
        addRenderableWidget(refresh);
        addRenderableWidget(saveBtn);
        addRenderableWidget(cancel);

        String startSel = initial != null ? initial.skinFileName() : null;
        list.setEntries(library.entries(), startSel);
        if (startSel != null) {
            SkinLibrary.Entry.Valid v = library.findByFilename(startSel);
            if (v != null) acceptEntry(v);
        }
        revalidate();
    }

    private void onSelectEntry(SkinLibrary.Entry e) {
        if (e instanceof SkinLibrary.Entry.Valid v) {
            acceptEntry(v);
        } else if (e instanceof SkinLibrary.Entry.Invalid inv) {
            errorMsg = inv.reason();
            stagedSkinEntry = null;
            stagedSkin = null;
        }
        revalidate();
    }

    private void acceptEntry(SkinLibrary.Entry.Valid v) {
        try {
            stagedSkin = library.loadAsSkin(v);
            stagedSkinEntry = v;
            errorMsg = null;
        } catch (Exception ex) {
            errorMsg = "Could not load skin: " + ex.getMessage();
            PlayerDisguise.LOGGER.warn(errorMsg, ex);
        }
    }

    private void refreshList() {
        library.refresh();
        String selected = stagedSkinEntry != null ? stagedSkinEntry.filename() : null;
        list.setEntries(library.entries(), selected);
        if (selected != null && library.findByFilename(selected) == null) {
            stagedSkinEntry = null;
            stagedSkin = null;
        }
        revalidate();
    }

    private void openSkinsFolder() {
        try {
            java.nio.file.Files.createDirectories(PlayerDisguiseClient.config().skinsDir());
        } catch (Exception ignored) {}
        Util.getPlatform().openUri(PlayerDisguiseClient.config().skinsDir().toUri());
    }

    private void revalidate() {
        String n = nameField.getValue();
        boolean nameOk = NAME_RE.matcher(n).matches();
        if (!nameOk) {
            errorMsg = "Name must be 3–16 chars, letters/digits/underscore only.";
        } else if (errorMsg != null && errorMsg.startsWith("Name must")) {
            errorMsg = null;
        }
        saveBtn.active = nameOk;
    }

    private void save() {
        String name = nameField.getValue();
        String filename = stagedSkinEntry != null ? stagedSkinEntry.filename() : null;
        String hash = stagedSkinEntry != null ? stagedSkinEntry.sha256() : null;
        onSave.accept(new Profile(name, filename, hash));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFF);
        if (errorMsg != null) {
            g.drawCenteredString(this.font,
                    Component.literal(errorMsg).withStyle(ChatFormatting.RED),
                    this.width / 2, this.height - 44, 0xFF5555);
        } else if (stagedSkinEntry != null) {
            String label = stagedSkinEntry.filename() + " (" + stagedSkinEntry.width() + "x" + stagedSkinEntry.height() + ")";
            g.drawCenteredString(this.font,
                    Component.literal(label).withStyle(ChatFormatting.GRAY),
                    this.width / 2, this.height - 44, 0xAAAAAA);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
