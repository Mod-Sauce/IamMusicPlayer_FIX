package dev.felnull.imp.client.gui.components;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.modsauce.otyacraftenginerenewed.client.gui.components.FixedListWidget;
import dev.felnull.imp.client.renderer.PlayerFaceRenderer;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The **uploader** list of the "Music Management" page: one uploader per row (avatar + name + total / deletable);
 * clicking a row = check all he uploaded, again = uncheck; the mark is three-state ({@code [x]} / {@code [-]} /
 * {@code [ ]}) from the page's {@code selectedIds}; rows with an empty {@code deletable} are gray, not clickable. */
public class ManageOwnersFixedListWidget extends IMPBaseFixedListWidget<ManageOwnersFixedListWidget.Owner> {
    /**
     * One row = one uploader.
     *
     * @param id        uploader UUID (avatar and check state)
     * @param name      display name, a UUID string when unavailable
     * @param total     how many songs he uploaded to the current playlist, including ones you may not delete
     * @param deletable the ids you can delete (empty = the row cannot be clicked)
     */
    public record Owner(@NotNull UUID id, @NotNull String name, int total, @NotNull List<UUID> deletable) {
    }

    /** The selection set maintained by the management page (decides the three-state mark; the same one as the music list). */
    private final Set<UUID> selectedIds;
    /**
     * What double-clicking a row's avatar does (the management page passes "show only this uploader's music"); null = disabled.
     */
    @Nullable
    private final java.util.function.Consumer<Owner> onAvatarDoubleClick;
    /** Double-click detection: the index of the last row whose avatar was hit + the timestamp (Otya's list has no double-click concept). */
    private int lastAvatarClickIndex = -1;
    private long lastAvatarClickAt;

    /** Double-click time window (milliseconds). */
    private static final long DOUBLE_CLICK_MS = 300L;
    /** Avatar hit rectangle; the avatar is drawn at (bX+2, bY+13) as 9×9 (see {@link #renderOneButton}), drawn 14×13
     * (x 0~14 / y 11~24) — the upper half of the left column (y 2~11) is the checkbox and stays untouched. */
    private static final int AVATAR_HIT_X0 = 0;
    private static final int AVATAR_HIT_X1 = 14;
    private static final int AVATAR_HIT_Y0 = 11;
    private static final int AVATAR_HIT_Y1 = 24;

    public ManageOwnersFixedListWidget(int x, int y, int width, int height, @NotNull Component message, int entryShowCount,
                                       @NotNull List<Owner> entryList, @Nullable PressEntry<Owner> onPressEntry,
                                       boolean selectable, @Nullable FixedListWidget<Owner> old,
                                       @NotNull Set<UUID> selectedIds) {
        this(x, y, width, height, message, entryShowCount, entryList, onPressEntry, selectable, old, selectedIds, null);
    }

    public ManageOwnersFixedListWidget(int x, int y, int width, int height, @NotNull Component message, int entryShowCount,
                                       @NotNull List<Owner> entryList, @Nullable PressEntry<Owner> onPressEntry,
                                       boolean selectable, @Nullable FixedListWidget<Owner> old,
                                       @NotNull Set<UUID> selectedIds,
                                       @Nullable java.util.function.Consumer<Owner> onAvatarDoubleClick) {
        super(x, y, width, height, message, entryShowCount, entryList, n -> Component.literal(n.name()),
                onPressEntry, selectable, old);
        this.selectedIds = selectedIds;
        this.onAvatarDoubleClick = onAvatarDoubleClick;
    }

    /** Avatar clicks are handled here, not by the base class — otherwise a double click would check all of this
     * player's music and uncheck it on the second; a click within 300ms triggers {@link #onAvatarDoubleClick}. */
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && onAvatarDoubleClick != null && isOnAvatar(mx, my)) {
            long now = System.currentTimeMillis();
            int idx = entryIndexAt(my);
            if (idx >= 0 && idx == lastAvatarClickIndex && now - lastAvatarClickAt <= DOUBLE_CLICK_MS) {
                lastAvatarClickIndex = -1;
                lastAvatarClickAt = 0L;
                var list = getEntryList();
                if (idx < list.size())
                    onAvatarDoubleClick.accept(list.get(idx));
            } else {
                lastAvatarClickIndex = idx;
                lastAvatarClickAt = now;
            }
            return true;   // Consumed: a single click on the avatar does not toggle the checkbox
        }
        return super.mouseClicked(mx, my, button);
    }

    /** Whether the mouse is inside a row's **avatar** rectangle (the same relative coordinates as drawing; the scrollbar is at the far right, so no false hit). */
    private boolean isOnAvatar(double mx, double my) {
        int idx = entryIndexAt(my);
        if (idx < 0)
            return false;
        int rowTop = getY() + (idx - getCurrentFirstEntryIndex()) * getIndividualHeight();
        int relX = (int) mx - getX();
        int relY = (int) my - rowTop;
        return relX >= AVATAR_HIT_X0 && relX <= AVATAR_HIT_X1 && relY >= AVATAR_HIT_Y0 && relY <= AVATAR_HIT_Y1;
    }

    /** Entry index for the mouse's vertical position (returns -1 when not on any row). */
    private int entryIndexAt(double my) {
        if (my < getY() || my >= getY() + getHeight())
            return -1;
        int idx = getCurrentFirstEntryIndex() + (int) ((my - getY()) / getIndividualHeight());
        return idx >= 0 && idx < getEntryList().size() ? idx : -1;
    }

    @Override
    protected void renderOneButton(GuiGraphics guiGraphics, Owner item, int lnum, int bnum, int bX, int bY,
                                   int mx, int my, float parTick, boolean selectedFromList) {
        drawSmartButtonBox(guiGraphics, bX, bY, getIndividualWidth(), getIndividualHeight(), this.getYImage(this.isEntryHovered(bnum)));

        int deletable = item.deletable().size();
        int selectedCount = 0;
        if (selectedIds != null) {
            for (UUID id : item.deletable())
                if (selectedIds.contains(id))
                    selectedCount++;
        }
        boolean allowed = deletable > 0;

        var boxColor = !allowed ? 0xFF7A7A7A : (selectedCount == 0 ? 0xFF444444 : 0xFFFFFF00);
        var mark = selectedCount == 0 ? "[ ]" : (selectedCount >= deletable ? "[x]" : "[-]");
        drawSmartFixedWidthText(guiGraphics, Component.literal(mark), bX + 2, bY + 2, 16, boxColor);
        // Lower cell of the left column: the avatar (same layout as the music list: +2 / +13, 9px)
        PlayerFaceRenderer.drawFace(guiGraphics.pose(), item.id(), bX + 2, bY + 13, 9);

        float sx = 18;
        var color = allowed ? 0xFFFFFFFF : 0xFF888888;
        drawSmartFixedWidthText(guiGraphics, Component.literal(item.name()), bX + sx, bY + 4,
                getIndividualWidth() - (int) sx - 2, color);
        drawSmartFixedWidthText(guiGraphics, Component.translatable("imp.text.manageOwnerCount", item.total(), deletable),
                bX + sx, bY + 15, getIndividualWidth() - (int) sx - 2, color);
    }
}
