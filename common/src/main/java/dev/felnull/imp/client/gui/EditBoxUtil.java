package dev.felnull.imp.client.gui;

import net.minecraft.client.gui.components.EditBox;

/** Input box write utility: fix the display window after a programmatic setValue — vanilla {@code EditBox#scrollTo}
 * keeps the stale {@code displayPos} when the cursor is at the end and the text fits, so the value renders from the
 * middle; {@link #setValueCursorEnd} zeroes the view, then cursor to the end (ID / BV); order must not be reversed. */
public final class EditBoxUtil {
    private EditBoxUtil() {
    }

    /** Write and keep the view at the start (touches no state other than the cursor position; does nothing if the value is unchanged). */
    public static void setValueShowHead(EditBox box, String value) {
        if (box == null || value == null || value.equals(box.getValue()))
            return;
        box.setValue(value);
        // setCursorPosition(0) goes through scrollTo(0): here pos(0) <= displayPos holds → displayPos is reset to zero
        box.setCursorPosition(0);
        box.setHighlightPos(0);
    }

    /** Write, pull the view to the start, then put the cursor at the end (for short identifier texts). */
    public static void setValueCursorEnd(EditBox box, String value) {
        if (box == null || value == null || value.equals(box.getValue()))
            return;
        box.setValue(value);
        box.setCursorPosition(0);
        box.setHighlightPos(0);
        // The order must not be reversed: calling moveCursorToEnd first lands in the fixed point above again
        box.moveCursorToEnd(false);
    }
}
