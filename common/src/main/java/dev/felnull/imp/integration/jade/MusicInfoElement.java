package dev.felnull.imp.integration.jade;

import dev.felnull.fnjl.util.FNStringUtil;
import dev.felnull.imp.block.BoomboxData;
import dev.felnull.imp.client.renderer.PlayImageRenderer;
import dev.felnull.imp.music.resource.Music;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.phys.Vec2;
import snownee.jade.api.ui.Element;

public class MusicInfoElement extends Element {

    private static final int IMAGE_SIZE = 24;
    private static final int PADDING = 4;
    private static final int LINE_HEIGHT = 10;
    private static final int WIDTH = 160;
    private static final int PROGRESS_HEIGHT = 3;

    private final BoomboxData data;
    private final Music music;

    public MusicInfoElement(BoomboxData data) {
        this.data = data;
        this.music = data.getSelectedMusic() != null ? data.getSelectedMusic() : data.getCassetteTapeMusic();
    }

    @Override
    public Vec2 getSize() {
        boolean showProgress = data.isPlaying()
                && music != null
                && music.getSource() != null
                && music.getSource().getDuration() > 0;

        int height = IMAGE_SIZE + PADDING * 2 + (showProgress ? PROGRESS_HEIGHT + 2 : 0);

        if (music != null && Screen.hasShiftDown()) {
            Font font = Minecraft.getInstance().font;
            int nameWidth = font.width(music.getName());
            boolean hasImage = music.getImage() != null && !music.getImage().isEmpty();
            int minWidth = (hasImage ? IMAGE_SIZE + PADDING : 0) + nameWidth + PADDING * 2;
            return new Vec2(Math.max(WIDTH, minWidth), height);
        }

        return new Vec2(WIDTH, height);
    }

    @Override
    public void render(GuiGraphics guiGraphics, float x, float y, float maxX, float maxY) {
        Font font = Minecraft.getInstance().font;
        int ix = Math.round(x) + PADDING;
        int iy = Math.round(y) + PADDING;

        boolean hasImage = music != null && music.getImage() != null && !music.getImage().isEmpty();
        boolean shiftDown = Screen.hasShiftDown();

        if (hasImage) {
            PlayImageRenderer.getInstance().draw(music.getImage(), guiGraphics.pose(), ix, iy, IMAGE_SIZE);
        }

        int textX = hasImage ? ix + IMAGE_SIZE + PADDING : ix;
        int textMaxWidth = Math.round(getCachedSize().x) - (textX - Math.round(x)) - PADDING;

        if (music != null) {
            String name = shiftDown ? music.getName() : truncate(font, music.getName(), textMaxWidth);
            guiGraphics.drawString(font, name, textX, iy, 0xFFFFFF, false);

            String author = music.getAuthor();
            if (author != null && !author.isEmpty()) {
                guiGraphics.drawString(font,
                        truncate(font, author, textMaxWidth),
                        textX, iy + LINE_HEIGHT, 0xAAAAAA, false);
            }
        }

        boolean showProgress = data.isPlaying()
                && music != null
                && music.getSource() != null
                && music.getSource().getDuration() > 0;

        if (showProgress) {
            long duration = music.getSource().getDuration();
            long position = data.getMusicPosition();
            float progress = (float) position / (float) duration;

            int progressY = iy + IMAGE_SIZE + 2;
            int progressWidth = Math.round(getCachedSize().x) - PADDING * 2;

            String timeText = FNStringUtil.getTimeProgress(position, duration);
            int timeWidth = font.width(timeText);
            int barWidth = progressWidth - timeWidth - PADDING;

            guiGraphics.fill(ix, progressY, ix + barWidth, progressY + PROGRESS_HEIGHT, 0x55FFFFFF);
            int filledWidth = Math.round(barWidth * progress);
            guiGraphics.fill(ix, progressY, ix + filledWidth, progressY + PROGRESS_HEIGHT, 0xFFFFFFFF);

            guiGraphics.drawString(font, timeText,
                    ix + barWidth + PADDING,
                    progressY + PROGRESS_HEIGHT / 2 - font.lineHeight / 2,
                    0xFF55FF55, false);
        }
    }

    private String truncate(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        while (font.width(text + "...") > maxWidth && !text.isEmpty()) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "...";
    }
}