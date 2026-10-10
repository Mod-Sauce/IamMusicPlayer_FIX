package dev.felnull.imp.client.gui.components;

import dev.felnull.imp.client.gui.IIMPSmartRender;
import dev.felnull.imp.client.renderer.PlayImageRenderer;
import dev.felnull.imp.music.resource.ImageInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.modsauce.otyacraftenginerenewed.client.gui.components.FixedListWidget;

import java.util.List;
import java.util.function.Consumer;

/**
 * One B站 video's parts as a pickable list: clicking a row selects that part, whose details are shown next to it and
 * which the play key starts. Layout follows {@link SpaceCategoryListWidget}; thumbnails only when the page passes one.
 */
public class BiliBiliPartsListWidget extends IMPBaseFixedListWidget<BiliBiliPartsListWidget.Row> {
    /**
     * One part row.
     *
     * @param page       the part number (1-based; {@code P1} is the video itself)
     * @param label      what to show next to the part marker
     * @param durationMs the part's length in ms (0 = unknown ⇒ {@code --:--})
     * @param selected   whether this is the part whose details are being shown (drawn "pressed")
     * @param playing    whether this is the part the manager is previewing right now (drawn cyan)
     * @param thumb      the part's first frame, or {@code null} when thumbnails are off / unknown
     */
    public record Row(int page, Component label, long durationMs, boolean selected, boolean playing,
                      @Nullable ImageInfo thumb) {
        public static Row of(int page, Component label, long durationMs, boolean selected, boolean playing,
                             @Nullable ImageInfo thumb) {
            return new Row(page, label, durationMs, selected, playing, thumb);
        }
    }

    private final Consumer<Row> onPick;

    public BiliBiliPartsListWidget(int x, int y, int width, int height, @NotNull Component message, int entryShowCount,
                                   @NotNull List<Row> entryList, @NotNull Consumer<Row> onPick,
                                   @Nullable FixedListWidget<Row> old) {
        super(x, y, width, height, message, entryShowCount, entryList, Row::label, null, false, old);
        this.onPick = onPick;
    }

    /** {@code m:ss} for the right-hand edge of a row ({@code --:--} while the length is unknown). */
    public static String durationText(long ms) {
        if (ms <= 0L)
            return "--:--";
        long total = ms / 1000L;
        return (total / 60L) + ":" + String.format("%02d", total % 60L);
    }

    /** Height of a row thumbnail (a row is 20 px, so 16 px leaves 2 px above and below). */
    public static final int THUMB_SIZE = 16;

    @Override
    protected void renderOneButton(GuiGraphics guiGraphics, Row item, int lnum, int bnum, int bX, int bY,
                                   int mx, int my, float parTick, boolean selectedFromList) {
        int rowWidth = getIndividualWidth();
        int rowHeight = getIndividualHeight();
        // The row whose details are shown is drawn "pressed"; the part playing right now is called out by colour below
        drawSmartButtonBox(guiGraphics, bX, bY, rowWidth, rowHeight,
                item.selected() ? 2 : this.getYImage(this.isEntryHovered(bnum)));

        int sx = 2;
        var thumb = item.thumb();
        if (thumb != null && !thumb.isEmpty()) {
            int size = Math.min(THUMB_SIZE, rowHeight - 4);
            // drawIfLoaded: while a cover is still downloading the loader would hand us an animated placeholder GIF,
            // which is what made the list flash - draw nothing until the real image is there.
            PlayImageRenderer.getInstance().drawIfLoaded(thumb, guiGraphics.pose(), bX + 2, bY + 2, size, false);
            sx += size + 2;
        }

        int color = item.playing() ? 0xFF00A0A0 : 0xFF000000;
        var marker = Component.literal("P" + item.page());
        int markerWidth = IIMPSmartRender.mc.font.width(marker) + 4;
        var duration = Component.literal(durationText(item.durationMs()));
        int durationWidth = IIMPSmartRender.mc.font.width(duration) + 3;
        drawSmartFixedWidthText(guiGraphics, marker, bX + sx, bY + 3, markerWidth, color);
        drawSmartFixedWidthText(guiGraphics, item.label(), bX + sx + markerWidth, bY + 3,
                rowWidth - sx - 5 - markerWidth - durationWidth, color);
        drawSmartText(guiGraphics, duration, bX + rowWidth - durationWidth, bY + 3, color);
    }

    /**
     * A click reports the row (select that part); the base implementation is deliberately not called — it would keep the
     * widget's own single selection, a second source of truth next to the page's "selected part".
     */
    @Override
    public void onPress(int num) {
        int index = getCurrentFirstEntryIndex() + num;
        var entries = getEntryList();
        if (index < 0 || index >= entries.size() || !isEntryHovered(num))
            return;
        onPick.accept(entries.get(index));
    }
}
