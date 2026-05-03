package com.weaversworkshop.playerdisguise.client.gui;

import com.weaversworkshop.playerdisguise.client.skin.SkinLibrary;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

public class SkinListWidget extends ObjectSelectionList<SkinListWidget.Row> {
    private final Consumer<SkinLibrary.Entry> onSelect;

    public SkinListWidget(Minecraft mc, int width, int height, int top, int itemHeight,
                          Consumer<SkinLibrary.Entry> onSelect) {
        super(mc, width, height, top, itemHeight);
        this.onSelect = onSelect;
    }

    public void setEntries(List<SkinLibrary.Entry> entries, String selectedFilename) {
        this.clearEntries();
        Row toSelect = null;
        for (SkinLibrary.Entry e : entries) {
            Row row = new Row(e);
            this.addEntry(row);
            if (selectedFilename != null && selectedFilename.equals(e.filename())) toSelect = row;
        }
        if (toSelect != null) {
            this.setSelected(toSelect);
            this.centerScrollOn(toSelect);
        }
    }

    @Override
    public int getRowWidth() {
        return this.width - 10;
    }

    @Override
    protected int getScrollbarPosition() {
        return this.getX() + this.width - 6;
    }

    public class Row extends ObjectSelectionList.Entry<Row> {
        final SkinLibrary.Entry entry;

        Row(SkinLibrary.Entry entry) { this.entry = entry; }

        @Override
        public Component getNarration() {
            return Component.literal(entry.filename());
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            SkinListWidget.this.setSelected(this);
            onSelect.accept(entry);
            return true;
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int rowWidth, int rowHeight,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            var font = Minecraft.getInstance().font;
            String label = entry.filename();
            int color = 0xFFFFFF;
            String detail;
            if (entry instanceof SkinLibrary.Entry.Valid v) {
                detail = v.width() + "x" + v.height() + ", " + v.bytes().length + " B";
            } else {
                color = 0xFF5555;
                detail = ((SkinLibrary.Entry.Invalid) entry).reason();
            }
            g.drawString(font, label, left + 4, top + 3, color);
            g.drawString(font,
                    Component.literal(detail).withStyle(ChatFormatting.GRAY),
                    left + 4, top + 14, 0xAAAAAA);
        }
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput out) {}
}
