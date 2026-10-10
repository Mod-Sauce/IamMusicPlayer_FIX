package dev.felnull.imp.client.gui.components;

import dev.felnull.imp.client.gui.IIMPSmartRender;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A button that looks like text but is clickable — still a widget (the screen dispatches clicks, no page hit testing);
 * width = text width ({@link #setMessage}), height fixed at {@link #HEIGHT} (glyph 9 + 1px underline).
 */
public class TextButton extends IMPButton implements IIMPSmartRender {
    /** Widget height: glyph height 9 + 1px underline. */
    public static final int HEIGHT = 10;

    /**
     * Normal text color (gold: distinct from the black text in the UI, hinting "this line is clickable").
     */
    private int color = 0xFFFFD24A;
    /** Text color on hover / focus (0xFF8A5A00, darker than normal — on a white background a brighter color would be invisible). */
    private int hoverColor = 0xFF8A5A00;
    /**
     * Deadline of the timed self-destruction (ms; 0 = no timer), checked by the widget itself in {@link #renderWidget}:
     * a page-{@code tick()} sync would miss a frame, since delete/undo happen inside a click event.
     */
    private long hideAt;

    public TextButton(int x, int y, Component text, OnPress onPress) {
        super(x, y, Math.max(1, IIMPSmartRender.mc.font.width(text)), HEIGHT, text, onPress);
    }

    /** Stay visible until {@code hideAtMillis}, then self-destruct (0 = cancel the timer). */
    public TextButton setHideAt(long hideAtMillis) {
        this.hideAt = hideAtMillis;
        return this;
    }

    /** Whether the self-destruct time has already passed. */
    public boolean isExpired() {
        return hideAt != 0L && System.currentTimeMillis() >= hideAt;
    }

    /** Set the text colors (default gold / hover dark brown (0xFF8A5A00)). */
    public TextButton setColors(int color, int hoverColor) {
        this.color = color;
        this.hoverColor = hoverColor;
        return this;
    }

    /** Recompute the width from the new text when the message changes (click area = that line of text itself). */
    @Override
    public void setMessage(Component message) {
        super.setMessage(message);
        setWidth(Math.max(1, IIMPSmartRender.mc.font.width(message)));
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Self-destruct on expiry: checked **here**, not relying on the page's tick (see the note on hideAt)
        if (isExpired()) {
            hideAt = 0L;
            this.visible = false;
            this.active = false;
            return;
        }
        boolean hv = isHoveredOrFocused();
        // Hover: darker color + the font's built-in underline (`ChatFormatting.UNDERLINE`), which is part of the glyph batch.
        Component msg = hv ? getMessage().copy().withStyle(ChatFormatting.UNDERLINE) : getMessage();
        drawSmartText(guiGraphics, msg, getX(), getY(), hv ? hoverColor : color);
    }
}
