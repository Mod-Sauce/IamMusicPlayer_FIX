package dev.felnull.imp.client.gui.components;

import dev.architectury.utils.value.Value;
import dev.felnull.imp.client.gui.screen.monitor.music_manager.MusicManagerMonitor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.modsauce.otyacraftenginerenewed.client.util.OERenderUtils;

/** Playback page loop widget: 8×7 icon, click cycles repeat one → sequential → shuffle (same as the boombox button).
 * Frames sit at y=164 in {@code widgets.png}; normal {@code x=frame*8}, hover {@code x=112+frame*8}, no scaling.
 * Order (0=right, 1=loop, 2=shuffle) is not state order, so {@link #FRAME_INDEX} maps it; states = {@code maxState}. */
public class LoopControlWidget extends AbstractWidget {
    /** Start x of the hover frames: the default "shift 16 pixels right" would overlap the third frame, so the hover
     * set lives in the blank area at x=112/120/128 (the three states occupy x=0..23). */
    private static final int HOVER_X = 112;

    /** Icon frame size: 8×7, no scaling. */
    private static final int FRAME_W = 8;
    private static final int FRAME_H = 7;

    /** Frame step and texture row. */
    private static final int FRAME_STEP = 8;
    private static final int FRAME_Y = 164;

    /**
     * State → frame of the row. The two orders differ (frame0=right, 1=loop, 2=shuffle vs state0=loop, 1=right, 2=shuffle), so {@code state*8} cannot be used directly.
     */
    private static final int[] FRAME_INDEX = {1, 0, 2};

    private final Value<Integer> loopState;
    private final int maxState;

    public LoopControlWidget(int x, int y, Value<Integer> loopState, int maxState) {
        super(x, y, FRAME_W, FRAME_H, Component.translatable("imp.widget.loopControl"));
        this.loopState = loopState;
        this.maxState = Math.max(0, maxState);
    }

    /** Convenience constructor for the two-state case (cassette deck). */
    public LoopControlWidget(int x, int y, Value<Integer> loopState) {
        this(x, y, loopState, 1);
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
        int z = isHoveredOrFocused() ? HOVER_X : 0;
        OERenderUtils.drawTexture(MusicManagerMonitor.WIDGETS_TEXTURE, guiGraphics.pose(),
                getX(), getY(), z + FRAME_INDEX[current()] * FRAME_STEP, FRAME_Y, FRAME_W, FRAME_H);
    }

    @Override
    public void onClick(double d, double e) {
        super.onClick(d, e);
        int target = (current() + 1) % (maxState + 1);
        dev.felnull.imp.client.music.link.LinkDebug.log("loop widget click",
                "current", current(), "target", target, "maxState", maxState);
        loopState.accept(target);
    }

    private int current() {
        Integer v = loopState.get();
        int st = v == null ? 0 : v;
        return Math.max(0, Math.min(maxState, st));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }
}
