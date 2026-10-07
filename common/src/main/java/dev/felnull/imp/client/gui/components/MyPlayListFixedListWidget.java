package dev.felnull.imp.client.gui.components;

import dev.felnull.imp.client.renderer.PlayImageRenderer;
import dev.felnull.imp.music.resource.MusicPlayList;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.modsauce.otyacraftenginerenewed.client.gui.components.FixedListWidget;
import org.modsauce.otyacraftenginerenewed.client.util.OEClientUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The left column of the "playlist" page (my playlists). The geometry and colors of these two actions are deliberately
 * public constants/static methods, because this list has two render paths that must match exactly: this widget, and
 * {@code PlayListMMMonitor#renderAppearance} drawing the rows by hand for the in-world display.
 */
public class MyPlayListFixedListWidget extends PlayListFixedListWidget {
    public static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm");

    /** In-row geometry: row width = widget width 100 - 9 (scrollbar) - 1 = 90, row height = 168 / 8 = 21. */
    public static final int INVITE_ROW_W = 90;
    /** The row the two actions sit on (the second row, where the date would be). */
    public static final int INVITE_ACT_Y = 12;
    /** The width one action text occupies (two characters) and the hit height. */
    public static final int INVITE_ACT_W = 18;
    public static final int INVITE_ACT_H = 9;
    /** Widen the hit rectangle a little (looks and clicks better), but keep it smaller than the gap between the two actions so the two blocks do not overlap. */
    private static final int INVITE_HIT_PAD = 2;
    private static final int INVITE_ACT_RIGHT_MARGIN = 2;
    private static final int INVITE_ACT_GAP = 4;
    /**
     * Colors of the two invite actions (deep green / deep red).
     */
    public static final int INVITE_ACCEPT_COLOR = 0xFF1E7A1E;
    public static final int INVITE_REJECT_COLOR = 0xFFA02020;

    private static final Component INVITE_ACCEPT_TEXT = Component.translatable("imp.text.inviteAccept");
    private static final Component INVITE_REJECT_TEXT = Component.translatable("imp.text.inviteReject");

    /** The x of "reject" (in-row local coordinates, right aligned). */
    public static int inviteRejectX(int rowWidth) {
        return rowWidth - INVITE_ACT_RIGHT_MARGIN - INVITE_ACT_W;
    }

    /** The x of "accept" (one gap to the left of "reject"). */
    public static int inviteAcceptX(int rowWidth) {
        return inviteRejectX(rowWidth) - INVITE_ACT_GAP - INVITE_ACT_W;
    }

    private final Function<MusicPlayList, Boolean> selected;
    /** Whether this row is "invited, not handled yet" (if so the second row draws the two actions, and clicking the row no longer accepts directly). */
    private final Function<MusicPlayList, Boolean> invited;
    /** What an invited row does when "accept" / "reject" is clicked (passed in by the page; null = draw only, no response). */
    @Nullable
    private final Consumer<MusicPlayList> onInviteAccept;
    @Nullable
    private final Consumer<MusicPlayList> onInviteReject;

    public MyPlayListFixedListWidget(int x, int y, @NotNull List<MusicPlayList> entryList, @Nullable PressEntry<MusicPlayList> onPressEntry, @Nullable FixedListWidget<MusicPlayList> old, Function<MusicPlayList, Boolean> selected, Function<MusicPlayList, Boolean> invited) {
        this(x, y, entryList, onPressEntry, old, selected, invited, null, null);
    }

    public MyPlayListFixedListWidget(int x, int y, @NotNull List<MusicPlayList> entryList, @Nullable PressEntry<MusicPlayList> onPressEntry, @Nullable FixedListWidget<MusicPlayList> old, Function<MusicPlayList, Boolean> selected, Function<MusicPlayList, Boolean> invited,
                                     @Nullable Consumer<MusicPlayList> onInviteAccept, @Nullable Consumer<MusicPlayList> onInviteReject) {
        super(x, y, 100, 168, Component.translatable("imp.fixedList.myPlaylist"), 8, entryList, onPressEntry, false, old);
        this.selected = selected;
        this.invited = invited;
        this.onInviteAccept = onInviteAccept;
        this.onInviteReject = onInviteReject;
    }

    @Override
    protected void renderOneButton(GuiGraphics guiGraphics, MusicPlayList item, int lnum, int bnum, int bX, int bY, int mx, int my, float parTick, boolean selected) {
        int k = this.getYImage(this.isEntryHovered(bnum));
        if (this.selected.apply(item))
            k = 0;
        drawSmartButtonBox(guiGraphics, bX, bY, getIndividualWidth(), getIndividualHeight(), k);

        var img = item.getImage();

        float sx = 1;

        if (!img.isEmpty()) {
            sx += getIndividualHeight() - 2 + 1;
            PlayImageRenderer.getInstance().draw(img, guiGraphics.pose(), bX + 1, bY + 1, getIndividualHeight() - 2);
        }

        drawSmartText(guiGraphics, Component.literal(OEClientUtils.getWidthOmitText(item.getName(), getIndividualWidth() - sx - 2, "...")), bX + sx, bY + 2);

        if (isInvited(item)) {
            // Invited row: the second row becomes the two actions and the date is not drawn (the row is only 90 wide, the date would fill the whole row)
            int w = getIndividualWidth();
            drawSmartFixedWidthText(guiGraphics, INVITE_ACCEPT_TEXT, bX + inviteAcceptX(w), bY + INVITE_ACT_Y, INVITE_ACT_W, INVITE_ACCEPT_COLOR);
            drawSmartFixedWidthText(guiGraphics, INVITE_REJECT_TEXT, bX + inviteRejectX(w), bY + INVITE_ACT_Y, INVITE_ACT_W, INVITE_REJECT_COLOR);
        } else {
            drawSmartFixedWidthText(guiGraphics, Component.literal(dateFormat.format(new Date(item.getCreateDate()))), bX + sx, bY + 12, getIndividualWidth() - sx - 2);
        }
    }

    /**
     * Handles clicks itself: on an invited row only a click on the "accept" / "reject" text counts, anything else is
     * swallowed; non-invited rows go to the base class, where a click selects this playlist.
     */
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && invited != null && !isScrollBarHovered()) {
            int idx = entryIndexAt(my);
            var list = getEntryList();
            if (idx >= 0 && idx < list.size() && isInvited(list.get(idx))) {
                int relX = (int) mx - getX();
                int relY = (int) my - (getY() + (idx - getCurrentFirstEntryIndex()) * getIndividualHeight());
                int w = getIndividualWidth();
                if (inInviteRect(relX, relY, inviteAcceptX(w))) {
                    if (onInviteAccept != null)
                        onInviteAccept.accept(list.get(idx));
                    return true;
                }
                if (inInviteRect(relX, relY, inviteRejectX(w))) {
                    if (onInviteReject != null)
                        onInviteReject.accept(list.get(idx));
                    return true;
                }
                return true;   // Anywhere else on an invited row: swallow it (neither select nor accept)
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private boolean isInvited(MusicPlayList item) {
        return invited != null && invited.apply(item);
    }

    /** Whether the mouse is inside the hit rectangle of an action text (in-row local coordinates; the hit box is slightly larger than the drawn text). */
    private static boolean inInviteRect(int relX, int relY, int actX) {
        return relX >= actX - INVITE_HIT_PAD && relX <= actX + INVITE_ACT_W + INVITE_HIT_PAD
                && relY >= INVITE_ACT_Y - INVITE_HIT_PAD && relY <= INVITE_ACT_Y + INVITE_ACT_H + INVITE_HIT_PAD;
    }

    /** The entry index for the mouse's vertical position (returns -1 when not on any row). */
    private int entryIndexAt(double my) {
        if (my < getY() || my >= getY() + getHeight())
            return -1;
        int idx = getCurrentFirstEntryIndex() + (int) ((my - getY()) / getIndividualHeight());
        return idx >= 0 ? idx : -1;
    }
}
