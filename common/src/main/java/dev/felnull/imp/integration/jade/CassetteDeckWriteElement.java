package dev.felnull.imp.integration.jade;

import dev.felnull.imp.blockentity.CassetteDeckBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import snownee.jade.api.ui.Element;
public class CassetteDeckWriteElement extends Element {
    private static final int PADDING = 4;
    private static final int PROGRESS_HEIGHT = 3;
    private static final int WIDTH = 160;

    private final int progress;
    private final int progressMax;

    public CassetteDeckWriteElement(CassetteDeckBlockEntity be) {
        this.progress = be.getCassetteWriteProgress();
        this.progressMax = be.getCassetteWriteProgressAll();
    }

    @Override
    public Vec2 getSize() {
        int fontHeight = Minecraft.getInstance().font.lineHeight;
        return new Vec2(WIDTH, fontHeight + PROGRESS_HEIGHT + PADDING * 2 + 2);
    }

    @Override
    public void render(GuiGraphics guiGraphics, float x, float y, float maxX, float maxY) {
        Font font = Minecraft.getInstance().font;
        int ix = Math.round(x) + PADDING;
        int iy = Math.round(y) + PADDING;
        int barWidth = WIDTH - PADDING * 2;

        int percent = (int) ((float) progress / (float) progressMax * 100);
        String label = Component.translatable("imp.text.writing").getString() + " " + percent + "%";
        guiGraphics.drawString(font, label, ix, iy, 0xFFFFFF, false);

        int progressY = iy + font.lineHeight + 2;
        guiGraphics.fill(ix, progressY, ix + barWidth, progressY + PROGRESS_HEIGHT, 0x55FFFFFF);
        int filledWidth = Math.round(barWidth * ((float) progress / (float) progressMax));
        guiGraphics.fill(ix, progressY, ix + filledWidth, progressY + PROGRESS_HEIGHT, 0xFFFFFFFF);
    }
}