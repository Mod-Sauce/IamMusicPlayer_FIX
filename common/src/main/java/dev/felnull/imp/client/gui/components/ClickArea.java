package dev.felnull.imp.client.gui.components;

import dev.felnull.imp.client.gui.IIMPSmartRender;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** A click area that draws nothing, for text the page itself paints (e.g. a link inside a 简介): the page keeps the text
 * — putting it in a widget means a wrong widget position, size or draw order silently deletes that stretch of
 * characters from the line. */
public class ClickArea extends AbstractWidget implements IIMPSmartRender {
    private final Consumer<ClickArea> onClick;

    /** The link this area belongs to, so the page can read it back (used as the widget's message). */
    public ClickArea(int x, int y, int width, int height, Component message, Consumer<ClickArea> onClick) {
        super(x, y, width, height, message);
        this.onClick = onClick;
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
        // Intentionally draws nothing: the page paints the text under this area
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || !this.active || !this.visible || !isMouseOver(mx, my))
            return false;
        onClick.accept(this);
        return true;
    }
}
