package dev.felnull.imp.client.gui.components;

import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.bilibili.BiliBiliSpaceModel;
import dev.felnull.imp.client.bilibili.BiliBiliSpaceUtil;
import dev.felnull.imp.client.gui.IIMPSmartRender;
import dev.felnull.imp.client.renderer.PlayImageRenderer;
import dev.felnull.imp.music.resource.ImageInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.modsauce.otyacraftenginerenewed.client.gui.components.FixedListWidget;

import java.util.List;

/**
 * The right-hand video list of the UP-space view: clicking a row toggles it. Ticked state comes from
 * {@link BiliBiliSpaceModel#isSelected(String)}, not the base widget's single selection (a click overwrites that).
 */
public class SpaceVideoListWidget extends IMPBaseFixedListWidget<BiliBiliSpaceUtil.SpaceVideo> {
    private final BiliBiliSpaceModel model;

    public SpaceVideoListWidget(int x, int y, int width, int height, @NotNull Component message, int entryShowCount,
                                @NotNull List<BiliBiliSpaceUtil.SpaceVideo> entryList, @NotNull BiliBiliSpaceModel model,
                                @Nullable FixedListWidget<BiliBiliSpaceUtil.SpaceVideo> old) {
        super(x, y, width, height, message, entryShowCount, entryList, n -> Component.literal(n.title()), null, false, old);
        this.model = model;
    }

    @Override
    protected void renderOneButton(GuiGraphics guiGraphics, BiliBiliSpaceUtil.SpaceVideo item, int lnum, int bnum,
                                   int bX, int bY, int mx, int my, float parTick, boolean selectedFromList) {
        int rowWidth = getIndividualWidth();
        int rowHeight = getIndividualHeight();
        drawSmartButtonBox(guiGraphics, bX, bY, rowWidth, rowHeight, this.getYImage(this.isEntryHovered(bnum)));

        boolean checked = model.isSelected(item.bvid());
        drawSmartFixedWidthText(guiGraphics, Component.literal(checked ? "[x]" : "[ ]"), bX + 2, bY + 3, 14,
                checked ? 0xFFFFFF00 : 0xFF444444);

        float sx = 18;
        var cover = item.cover();
        if (showCover() && !cover.isBlank()) {
            PlayImageRenderer.getInstance().draw(new ImageInfo(ImageInfo.ImageType.URL, cover),
                    guiGraphics.pose(), bX + 17, bY + 1, rowHeight - 2, true);
            sx += rowHeight - 2 + 2;
        }

        var length = Component.literal(formatLength(item.durationMs()));
        float lengthWidth = IIMPSmartRender.mc.font.width(length) + 3;
        drawSmartFixedWidthText(guiGraphics, Component.literal(item.title()), bX + sx, bY + 3,
                rowWidth - sx - lengthWidth - 3);
        drawSmartText(guiGraphics, length, bX + rowWidth - lengthWidth, bY + 3, 0xFF333333);
    }

    private static boolean showCover() {
        return IamMusicPlayer.getConfig().bilibiliConfig.loadVideoImage;
    }

    /** Bilibili's own clock format: {@code 22:02}, and {@code 1:02:03} once the video passes an hour. */
    private static String formatLength(long durationMs) {
        long total = Math.max(0, durationMs) / 1000L;
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        long seconds = total % 60;
        return hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, seconds)
                : String.format("%d:%02d", minutes, seconds);
    }

    /** A click toggles that row's tick; the selection itself lives in the model, so it survives a filter change. */
    @Override
    public void onPress(int num) {
        int index = getCurrentFirstEntryIndex() + num;
        var entries = getEntryList();
        if (index < 0 || index >= entries.size() || !isEntryHovered(num))
            return;
        model.toggleSelected(entries.get(index).bvid());
    }
}
