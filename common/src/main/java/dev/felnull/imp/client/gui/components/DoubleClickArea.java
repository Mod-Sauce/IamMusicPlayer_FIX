package dev.felnull.imp.client.gui.components;

import dev.felnull.imp.client.gui.IIMPSmartRender;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A click area that draws nothing, only recognizing a "double click": a single click does not swallow the event (returns
 * false) and only a second click within 300ms fires the callback, so clicks on other widgets/screens still get through.
 */
public class DoubleClickArea extends AbstractWidget implements IIMPSmartRender {
    /** Double-click time window (milliseconds). */
    private static final long DOUBLE_CLICK_MS = 300L;
    @Nullable
    private final Runnable onDoubleClick;
    /** Timestamp of the last single click (0 = no "half" double click waiting right now). */
    private long lastClickAt;

    public DoubleClickArea(int x, int y, int width, int height, Component message, @Nullable Runnable onDoubleClick) {
        super(x, y, width, height, message);
        this.onDoubleClick = onDoubleClick;
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
        // Intentionally draws nothing: the page itself draws the music name under this area
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || !this.active || !this.visible || !isMouseOver(mx, my))
            return false;
        long now = System.currentTimeMillis();
        boolean dbl = now - lastClickAt <= DOUBLE_CLICK_MS;
        lastClickAt = dbl ? 0L : now;
        if (dbl && onDoubleClick != null) {
            onDoubleClick.run();
            return true;
        }
        return false;
    }
}
