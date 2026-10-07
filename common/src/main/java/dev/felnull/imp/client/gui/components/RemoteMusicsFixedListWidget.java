package dev.felnull.imp.client.gui.components;

import dev.felnull.imp.client.renderer.PlayImageRenderer;
import dev.felnull.imp.music.resource.Music;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.modsauce.otyacraftenginerenewed.client.util.OEClientUtils;
import org.modsauce.otyacraftenginerenewed.client.util.OERenderUtils;

import java.util.List;

public class RemoteMusicsFixedListWidget extends MusicsFixedListWidget {
    /** Marker for the currently playing track, as a string prefix only — no drawing call touched, because this list has
     * two render paths (the GUI uses this widget, the in-world display hand-draws the rows in
     * {@code RemotePlayBackSelectBMonitor#renderAppearance}) and both must match exactly. */
    public static final String CURRENT_MARKER = "▶ ";

    /** The track currently playing (null = no marker). Set by the page on every list refresh. */
    @Nullable
    private java.util.UUID currentMusic;

    public RemoteMusicsFixedListWidget(int x, int y, List<Music> list, PressEntry<Music> onPressEntry, @Nullable RemoteMusicsFixedListWidget old) {
        super(x, y, 129, 35, Component.translatable("imp.fixedList.myMusic"), 5, list, onPressEntry, false, old);
    }

    /** The page tells it "this one is now playing" (null = clear the marker). */
    public void setCurrentMusic(@Nullable java.util.UUID musicId) {
        this.currentMusic = musicId;
    }

    /**
     * Display text of a row (the playing track carries the marker). Both render paths share this method, so the GUI and the in-world display draw identical text.
     */
    public static String rowLabel(Music m, @Nullable java.util.UUID currentMusic) {
        return currentMusic != null && currentMusic.equals(m.getUuid()) ? CURRENT_MARKER + m.getName() : m.getName();
    }

    /**
     * Index of the current first row (protected in the base class, opened here for the page's "scroll to an entry + self-correct" use).
     */
    public int currentFirstEntryIndex() {
        return getCurrentFirstEntryIndex();
    }

    @Override
    protected void renderOneButton(GuiGraphics guiGraphics, Music item, int lnum, int bnum, int bX, int bY, int mx, int my, float parTick, boolean selected) {
        drawSmartButtonBox(guiGraphics, bX, bY, getIndividualWidth(), getIndividualHeight(), this.getYImage(this.isEntryHovered(bnum)));

        float sx = 1;
        var img = item.getImage();
        if (!img.isEmpty()) {
            sx += getIndividualHeight() - 2 + 1;
            PlayImageRenderer.getInstance().draw(img, guiGraphics.pose(), bX + 1, bY + 1, getIndividualHeight() - 2);
        }

        guiGraphics.pose().pushPose();
        float sc = 0.7f;
        OERenderUtils.poseScaleAll(guiGraphics.pose(), sc);
        drawSmartText(guiGraphics, Component.literal(OEClientUtils.getWidthOmitText(rowLabel(item, currentMusic), getIndividualWidth() - sx - 2 + 47, "...")), (bX + sx) / sc, (bY + 1f) / sc);
        guiGraphics.pose().popPose();
    }
}
