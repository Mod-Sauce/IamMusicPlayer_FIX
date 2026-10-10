package dev.felnull.imp.client.gui.components;

import dev.felnull.imp.client.gui.screen.BoomboxScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.modsauce.otyacraftenginerenewed.client.util.OERenderUtils;

/**
 * 19×13 icon key in the boombox's own frame for client-side actions — not a {@link BoomboxButton}: that one needs a {@code BoomboxData.ButtonType} and sends a press packet to the block.
 */
public class BoomboxIconButton extends IMPButton {
    private final ResourceLocation iconTexture;
    private final int iconStX;
    private final int iconStY;
    private final int iconW;
    private final int iconH;
    /** Draws the lit frames (2/3) instead of the normal ones (0/1). */
    private boolean lit;

    public BoomboxIconButton(int x, int y, Component msg, OnPress onPress, ResourceLocation iconTexture,
                             int iconStX, int iconStY, int iconW, int iconH) {
        super(x, y, 19, 13, msg, onPress);
        this.iconTexture = iconTexture;
        this.iconStX = iconStX;
        this.iconStY = iconStY;
        this.iconW = iconW;
        this.iconH = iconH;
    }

    public void setLit(boolean lit) {
        this.lit = lit;
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
        // frames as BoomboxButton: 0/1 = off/hover, 2/3 = lit/hover (the two background colours are the state)
        int r = (lit ? 2 : 0) + (isHoveredOrFocused() ? 1 : 0);
        float ix = getX() + (width - iconW) / 2f;
        float iy = getY() + (height - iconH) / 2f;
        OERenderUtils.drawTexture(BoomboxScreen.BG_TEXTURE, guiGraphics.pose(), getX(), getY(), r * 19, 175, 19, 13);
        OERenderUtils.drawTexture(iconTexture, guiGraphics.pose(), ix, iy, iconStX, iconStY, iconW, iconH);
        // When it is unavailable only the icon is dimmed: greying the whole button makes a black background look translucent
        if (!this.active)
            guiGraphics.fill((int) ix, (int) iy, (int) ix + iconW, (int) iy + iconH, 0x99000000);
    }
}
