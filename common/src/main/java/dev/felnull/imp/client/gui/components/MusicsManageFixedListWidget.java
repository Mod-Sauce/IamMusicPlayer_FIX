package dev.felnull.imp.client.gui.components;

import dev.felnull.imp.client.bilibili.BiliBiliParts;
import dev.felnull.imp.client.bilibili.BiliBiliUtil;
import dev.felnull.imp.client.music.media.IMPMusicMedias;
import dev.felnull.imp.client.renderer.PlayImageRenderer;
import dev.felnull.imp.music.resource.Music;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.modsauce.otyacraftenginerenewed.client.gui.components.FixedListWidget;
import dev.felnull.imp.client.renderer.PlayerFaceRenderer;

import java.util.function.Function;

/**
 * The music list of the "Music Management" page: clicking a whole row toggles its checkbox; row height 27, no uploader
 * name. Row colors: unchecked {@code #444444} / checked {@code #FFFF00} / not selectable {@code #7A7A7A}.
 */
public class MusicsManageFixedListWidget extends IMPBaseFixedListWidget<Music> {
    private final Function<Music, Boolean> canSelect;
    /** Whether a row draws as checked: the page's own set — the base list's {@code selected} is overwritten by a click. */
    private final java.util.Set<java.util.UUID> selectedIds;

    public MusicsManageFixedListWidget(int x, int y, int width, int height, @NotNull Component message, int entryShowCount,
                                       @NotNull java.util.List<Music> entryList, @Nullable PressEntry<Music> onPressEntry,
                                       boolean selectable, @Nullable FixedListWidget<Music> old,
                                       @NotNull Function<Music, Boolean> canSelect,
                                       @NotNull java.util.Set<java.util.UUID> selectedIds) {
        super(x, y, width, height, message, entryShowCount, entryList, n -> Component.literal(n.getName()), onPressEntry, selectable, old);
        this.canSelect = canSelect;
        this.selectedIds = selectedIds;
    }

    @Override
    protected void renderOneButton(GuiGraphics guiGraphics, Music item, int lnum, int bnum, int bX, int bY,
                                   int mx, int my, float parTick, boolean selectedFromList) {
        drawSmartButtonBox(guiGraphics, bX, bY, getIndividualWidth(), getIndividualHeight(), this.getYImage(this.isEntryHovered(bnum)));

        boolean allowed = canSelect.apply(item);
        // Judged by the **management page's selection set**, not the single-selection state given by the base class (see the note on selectedIds)
        boolean checked = selectedIds != null && selectedIds.contains(item.getUuid());
        var img = item.getImage();

        // Box colors: unchecked #444444 (visible on the #b9b9b9 row box) / checked bright yellow #FFFF00 / not selectable #7A7A7A.
        var boxColor = !allowed ? 0xFF7A7A7A : (checked ? 0xFFFFFF00 : 0xFF444444);
        drawSmartFixedWidthText(guiGraphics, Component.literal(checked ? "[x]" : "[ ]"), bX + 2, bY + 2, 16, boxColor);
        // Lower cell of the left column: the uploader avatar
        PlayerFaceRenderer.drawFace(guiGraphics.pose(), item.getOwner(), bX + 2, bY + 13, 9);

        float sx = 18;
        if (!img.isEmpty()) {
            sx += getIndividualHeight() - 2 + 1;
            PlayImageRenderer.getInstance().draw(img, guiGraphics.pose(), bX + 18, bY + 1, getIndividualHeight() - 2, false);
        }

        var nameColor = allowed ? 0xFFFFFFFF : 0xFF888888;
        drawSmartFixedWidthText(guiGraphics, Component.literal(item.getName()), bX + sx, bY + 4, getIndividualWidth() - sx - 2, nameColor);
        drawSmartFixedWidthText(guiGraphics, Component.literal(item.getAuthor()), bX + sx, bY + 15, 90, nameColor);

        // Part badge (`2P`), right-aligned on the author line: the author is cut at 90 px, so a long name cannot run
        // into it. See partBadge for what a badge does and does not promise.
        var badge = partBadge(item);
        if (!badge.isEmpty())
            drawSmartFixedWidthText(guiGraphics, Component.literal(badge), bX + getIndividualWidth() - 17, bY + 15, 15, 0xFF55FFFF);
    }

    /**
     * Badge of one row: {@code 2P} / {@code 3P} when that Bilibili video's parts are already known locally, otherwise
     * an empty string. A Bilibili row **without** a badge has never been looked up and costs one request (the submission list carries
     * no part count).
     */
    public static String partBadge(Music item) {
        var source = item == null ? null : item.getSource();
        if (source == null || !IMPMusicMedias.BILIBILI.getName().equals(source.getLoaderType()))
            return "";
        // An already expanded part (`BV…-p2`) is a row of its own and has nothing left to expand
        if (BiliBiliUtil.pageOf(source.getIdentifier()) > 1)
            return "";
        var parts = BiliBiliParts.known(source.getIdentifier());
        return parts.size() > 1 ? (parts.size() + "P") : "";
    }
}
