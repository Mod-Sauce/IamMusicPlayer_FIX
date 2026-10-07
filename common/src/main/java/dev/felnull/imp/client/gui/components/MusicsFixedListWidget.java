package dev.felnull.imp.client.gui.components;

import dev.felnull.imp.client.bilibili.BiliBiliArcTags;
import dev.felnull.imp.client.bilibili.BiliBiliUtil;
import dev.felnull.imp.client.music.media.IMPMusicMedias;
import dev.felnull.imp.client.renderer.PlayImageRenderer;
import dev.felnull.imp.music.resource.Music;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import dev.felnull.imp.client.gui.PlayerNameUtil;
import dev.felnull.imp.client.renderer.PlayerFaceRenderer;
import org.modsauce.otyacraftenginerenewed.client.gui.components.FixedListWidget;

import java.util.Date;
import java.util.List;

public class MusicsFixedListWidget extends IMPBaseFixedListWidget<Music> {
    public static final Component UNKNOWN_PLAYER_TEXT = Component.translatable("imp.text.unknownPlayer");

    /** Whether the row time follows the 「投稿日期」 sort. */
    private boolean byPublishDate;

    public MusicsFixedListWidget(int x, int y, int width, int height, @NotNull Component message, int entryShowCount, @NotNull List<Music> entryList, @Nullable PressEntry<Music> onPressEntry, boolean selectable, @Nullable FixedListWidget<Music> old) {
        super(x, y, width, height, message, entryShowCount, entryList, n -> Component.literal(n.getName()), onPressEntry, selectable, old);
    }

    public void setByPublishDate(boolean byPublishDate) {
        this.byPublishDate = byPublishDate;
    }

    /** Row time: the B站 upload time while sorted by 「投稿日期」 (「未记录」 when unknown), else the date added. */
    public static Component rowDate(Music item, boolean byPublishDate) {
        var source = item.getSource();
        if (byPublishDate && source != null && IMPMusicMedias.BILIBILI.getName().equals(source.getLoaderType())) {
            // by the bare video number: an expanded part carries a "-pN" suffix, the cache is keyed by bvid
            long ms = BiliBiliArcTags.getInstance().pubdateOf(BiliBiliUtil.stripPage(source.getIdentifier()));
            if (ms > 0L)
                return Component.literal(MyPlayListFixedListWidget.dateFormat.format(new Date(ms)));
            return Component.translatable("imp.text.publishDateUnknown");
        }
        return Component.literal(MyPlayListFixedListWidget.dateFormat.format(new Date(item.getCreateDate())));
    }

    /*@Override
    protected void renderOneButton(PoseStack poseStack, Music item, int lnum, int bnum, int bX, int bY, int mx, int my, float parTick, boolean selected) {
        drawSmartButtonBox(poseStack, bX, bY, getIndividualWidth(), getIndividualHeight(), this.getYImage(this.isEntryHovered(bnum)));
        var img = item.getImage();
        float sx = 2;

        if (!img.isEmpty()) {
            sx += getIndividualHeight() - 2 + 1;
            PlayImageRenderer.getInstance().draw(img, poseStack, bX + 1, bY + 1, getIndividualHeight() - 2, false);
        }

        drawSmartFixedWidthText(poseStack, Component.literal(item.getName()), bX + sx, bY + 2, getIndividualWidth() - sx - 2);
        drawSmartFixedWidthText(poseStack, Component.literal(item.getAuthor()), bX + sx, bY + 13, 90);
        OERenderUtils.drawPlayerFace(poseStack, item.getOwner(), bX + sx, bY + 23, 9);
        var pname = OEClientUtils.getPlayerNameByUUID(item.getOwner()).map(n -> (Component) Component.literal(n)).orElse(UNKNOWN_PLAYER_TEXT);
        drawSmartFixedWidthText(poseStack, pname, bX + sx + 12, bY + 24, 90);
        drawSmartFixedWidthText(poseStack, Component.literal(MyPlayListFixedListWidget.dateFormat.format(new Date(item.getCreateDate()))), bX + sx + 88 + 15, bY + 24, 90);
    }*/

    @Override
    protected void renderOneButton(GuiGraphics guiGraphics, Music item, int lnum, int bnum, int bX, int bY, int mx, int my, float parTick, boolean selected) {
        drawSmartButtonBox(guiGraphics, bX, bY, getIndividualWidth(), getIndividualHeight(), this.getYImage(this.isEntryHovered(bnum)));
        var img = item.getImage();
        float sx = 2;

        if (!img.isEmpty()) {
            sx += getIndividualHeight() - 2 + 1;
            PlayImageRenderer.getInstance().draw(img, guiGraphics.pose(), bX + 1, bY + 1, getIndividualHeight() - 2, false);
        }

        drawSmartFixedWidthText(guiGraphics, Component.literal(item.getName()), bX + sx, bY + 2, getIndividualWidth() - sx - 2);
        drawSmartFixedWidthText(guiGraphics, Component.literal(item.getAuthor()), bX + sx, bY + 13, 90);
        PlayerFaceRenderer.drawFace(guiGraphics.pose(), item.getOwner(), bX + sx, bY + 23, 9);
        var pname = PlayerNameUtil.serverNameComponent(item.getOwnerName(), item.getOwner(), UNKNOWN_PLAYER_TEXT);
        drawSmartFixedWidthText(guiGraphics, pname, bX + sx + 12, bY + 24, 90);
        drawSmartFixedWidthText(guiGraphics, rowDate(item, byPublishDate), bX + sx + 88 + 15, bY + 24, 90);
    }
}
