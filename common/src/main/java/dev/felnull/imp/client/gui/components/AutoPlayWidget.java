package dev.felnull.imp.client.gui.components;

import dev.felnull.imp.client.gui.IIMPSmartRender;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/**
 * **Green text toggle** at the bottom of the playback page ("auto play" / "stop after this"): it only says **whether to continue** — which track follows is the loop button's three states, and the two never override each other.
 */
public class AutoPlayWidget extends AbstractWidget implements IIMPSmartRender {
    private static final Component AUTO_PLAY_CONTROL = Component.translatable("imp.widget.autoPlay");
    private static final int COLOR_NORMAL = 0XFF115D0E;
    private static final int COLOR_HOVER = 0XFF007F06;

    private final BooleanSupplier autoPlay;
    private final Runnable onToggle;

    public AutoPlayWidget(int x, int y, int width, BooleanSupplier autoPlay, Runnable onToggle) {
        super(x, y, width, 10, AUTO_PLAY_CONTROL);
        this.autoPlay = autoPlay;
        this.onToggle = onToggle;
    }

    /** Current text: auto play / stop after this. */
    public Component getText() {
        return Component.translatable(autoPlay.getAsBoolean() ? "imp.text.autoPlay" : "imp.text.autoPlay.stop");
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
        // Centered on the **right edge** of the widget: when the text grows ("stop after this", 5 characters)
        // it expands to the left instead of pushing into the volume area on the right.
        drawSmartCenterText(guiGraphics, getText(), getX() + width / 2f, getY() + 2,
                isHoveredOrFocused() ? COLOR_HOVER : COLOR_NORMAL);
    }

    @Override
    public void onClick(double d, double e) {
        super.onClick(d, e);
        onToggle.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }
}
