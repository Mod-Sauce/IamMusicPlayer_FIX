package dev.felnull.imp.client.gui.components;

import dev.felnull.imp.block.BoomboxData;
import dev.felnull.imp.client.gui.screen.BoomboxScreen;
import dev.felnull.imp.client.gui.screen.monitor.music_manager.MusicManagerMonitor;
import net.minecraft.client.gui.GuiGraphics;
import org.modsauce.otyacraftenginerenewed.client.util.OERenderUtils;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class BoomboxButton extends IMPButton {
    private final BoomboxData.ButtonType type;
    private final Supplier<BoomboxData.Buttons> buttons;
    private final BooleanSupplier downShift;

    public BoomboxButton(int x, int y, BoomboxData.ButtonType type, OnPress onPress, Supplier<BoomboxData.Buttons> buttons) {
        this(x, y, type, onPress, buttons, () -> false);
    }

    public BoomboxButton(int x, int y, BoomboxData.ButtonType type, OnPress onPress, Supplier<BoomboxData.Buttons> buttons, BooleanSupplier downShift) {
        super(x, y, 19, 13, type.getComponent(), onPress);
        this.type = type;
        this.buttons = buttons;
        this.downShift = downShift;
    }

    /*@Override
    public void renderWidget(PoseStack poseStack, int i, int j, float f) {
        int r = isHoveredOrFocused() ? 1 : 0;
        if (type.getState(buttons.get()))
            r += 2;
        OERenderUtils.drawTexture(BoomboxScreen.BG_TEXTURE, poseStack, getX(), getY(), r * 19, 175, 19, 13);

        float zx = ((float) width - 9f) / 2f;
        float zy = ((float) height - 9f) / 2f;
        OERenderUtils.drawTexture(BoomboxScreen.BG_TEXTURE, poseStack, getX() + zx, getY() + zy, (type.ordinal() - 1) * 9, 188 + (downShift.getAsBoolean() ? 9 : 0), 9, 9);
    }*/

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
        int r = isHoveredOrFocused() ? 1 : 0;
        // [Three states] The loop button always uses the **lit** background frame (all three states lit),
        // the state difference is left entirely to the icon; all other buttons show on/off by background brightness.
        if (type == BoomboxData.ButtonType.LOOP)
            r = 2;
        else if (type.getState(buttons.get()))
            r += 2;
        OERenderUtils.drawTexture(BoomboxScreen.BG_TEXTURE, guiGraphics.pose(), getX(), getY(), r * 19, 175, 19, 13);

        float zx = ((float) width - 9f) / 2f;
        float zy = ((float) height - 9f) / 2f;
        var bs = buttons.get();

        if (type == BoomboxData.ButtonType.LOOP && bs.loopState() != 2) {
            // [States 0 / 1] 8×7 frames from the y=164 row, no scaling; the gray copies sit at x=96 / x=104.
            // State order and frame order differ, so the x comes from a lookup; only the draw position moves — the texture row must stay 164 or the loop arrow's top edge is cut off.
            int grayX = bs.loopState() == 0 ? 104 : 96;   // state0 loop / state1 right
            float dy = bs.loopState() == 0 ? 2f : 1f;   // state0 loop (7 rows) / state1 right (5 rows)
            OERenderUtils.drawTexture(MusicManagerMonitor.WIDGETS_TEXTURE, guiGraphics.pose(),
                    getX() + zx, getY() + zy + dy, grayX, 164, 8, 7);
            return;
        }

        // [State 2] Shuffle: the 9×9 at x=126 on the icon row of boombox_base.png
        OERenderUtils.drawTexture(BoomboxScreen.BG_TEXTURE, guiGraphics.pose(), getX() + zx, getY() + zy,
                iconX(bs), 188 + (downShift.getAsBoolean() ? 9 : 0), 9, 9);
    }

    /** Icon cell x: only state 2 of the loop button uses the new icon (x=126), the rest keep the original formula. */
    private int iconX(BoomboxData.Buttons bs) {
        if (type == BoomboxData.ButtonType.LOOP && bs.loopState() == 2)
            return 126;
        return (type.ordinal() - 1) * 9;
    }

    public BoomboxData.ButtonType getType() {
        return type;
    }

}
