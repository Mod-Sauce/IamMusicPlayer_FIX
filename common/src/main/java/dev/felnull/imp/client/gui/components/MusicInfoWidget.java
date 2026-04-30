package dev.felnull.imp.client.gui.components;

import dev.felnull.fnjl.util.FNStringUtil;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.block.BoomboxData;
import dev.felnull.imp.client.gui.IIMPSmartRender;
import dev.felnull.imp.client.gui.screen.monitor.music_manager.MusicManagerMonitor;
import dev.felnull.imp.client.music.lyric.IMPLyricGetter;
import dev.felnull.imp.client.music.lyric.LyricGetter;
import dev.felnull.imp.client.renderer.PlayImageRenderer;
import dev.felnull.imp.music.resource.Lyric;
import dev.felnull.imp.music.resource.Music;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.modsauce.otyacraftenginerenewed.client.util.OEClientUtils;
import org.modsauce.otyacraftenginerenewed.client.util.OERenderUtils;

public class MusicInfoWidget extends AbstractWidget implements IIMPSmartRender {
    private static final Component LOADING_MUSIC_TEXT = Component.translatable("imp.text.musicLoading");
    protected static final ResourceLocation PLAYING_BG_TEXTURE = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "textures/gui/container/boombox/monitor/playing.png");
    protected static final ResourceLocation PLAYING_IMAGE_TEXTURE = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "textures/gui/container/boombox/monitor/playing_image.png");
    @Nullable
    protected Music music;
    private final int baseHeight;
    @Nullable
    private BoomboxData data;

    @Nullable
    private Lyric lyric;
    @Nullable
    private Music oldMusic;
    @Nullable
    private LyricGetter lyricGetter;
    public MusicInfoWidget(int x, int y, int w, int h, int baseHeight) {
        super(x, y, w, h, Component.empty());
        this.music = null;
        this.baseHeight = baseHeight;
    }

    public void setMusic(Music music) {
        if(music != null && oldMusic != null && !music.getUuid().equals(oldMusic.getUuid())) {
            this.music = music;
            setChanged();
        }
        if(music != null && oldMusic == null){
            this.music = music;
            setChanged();
        }
        if(music == null && oldMusic != null){
            this.music = null;
            setChanged();
        }
        this.music = music;
        oldMusic = this.music;
    }

    public void setData(@Nullable BoomboxData data) {
        this.data = data;
    }

    private void setChanged(){
        lyric = null;
        if(lyricGetter != null){
            lyricGetter.stop();
            lyricGetter = null;
        }
        if(music == null)return;
        var getter = IMPLyricGetter.getGetter(music.getSource());
        if(getter != null){
            lyricGetter = getter;
            lyricGetter.run(music.getSource());
        }
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float d) {
        if(music == null || data == null)return;
        if(lyric == null)
            height = 36;
        else if(lyric.hasTrans())
            height = 59;
        else
            height = 49;
        OERenderUtils.drawFill(guiGraphics.pose(), getX(), getY(), width + getX(), height + getY(),
                0xFFDCDCDC);
        OERenderUtils.drawFill(guiGraphics.pose(), getX() + 1, getY() + 1, width + getX() - 1, height + getY() - 1, 0xFFFFFFFF);

        var poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(0, mc.font.lineHeight + 3, OERenderUtils.MIN_BREADTH * 2);
        OERenderUtils.drawTexture(PLAYING_BG_TEXTURE, poseStack, getX(), getY(), 0f, 0f, width, baseHeight, width, baseHeight);
        poseStack.popPose();

        if (!music.getImage().isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(0, 0, OERenderUtils.MIN_BREADTH * 3);
            OERenderUtils.drawTexture(PLAYING_IMAGE_TEXTURE, poseStack, getX(), getY(), 0, 0, width, baseHeight, width, baseHeight);
            poseStack.popPose();
        }

        int sx = 2;
        var renderer = PlayImageRenderer.getInstance();
        if (!music.getImage().isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(0, 0, OERenderUtils.MIN_BREADTH * 4);
            renderer.draw(music.getImage(), guiGraphics.pose(), getX() + 1, getY() + 1, baseHeight - 2);
            sx += baseHeight - 2;
            poseStack.popPose();
        }
        drawSmartCenterText(guiGraphics, Component.translatable(OEClientUtils.getWidthOmitText(music.getName(),
                        width - sx - 15,
                        "...")).withStyle(ChatFormatting.BOLD),
                getX() + sx + (width - sx - 2f) / 2f, getY() + 3);
        drawSmartCenterText(guiGraphics, Component.translatable(OEClientUtils.getWidthOmitText(music.getAuthor(),
                        width - sx - 15,
                        "...")),
                getX() + sx + (width - sx - 2f) / 2f, getY() + 3 + mc.font.lineHeight + 1);

        OERenderUtils.drawFill(guiGraphics.pose(), getX(), getY() + baseHeight, width + getX(), getY() + baseHeight - 1,
                0xFFDCDCDC);

        var process = music.getSource() == null ? 0 :
                (float) data.getMusicPosition() / (float) music.getSource().getDuration();
        var processStartY = baseHeight - 8 + getY();
        var processStartX = music.getImage().isEmpty() ? getX() + 5 : baseHeight - 2 + getX() + 4;
        var processWidth = music.getImage().isEmpty() ? width - baseHeight + 25 : width - baseHeight - 5;

        var ptx = LOADING_MUSIC_TEXT;
        if (!(music.getSource() == null))
            ptx = Component.translatable(FNStringUtil.getTimeProgress(data.getMusicPosition(),
                    music.getSource().getDuration()));
        var ptxWidth = mc.font.width(ptx);
        drawSmartText(guiGraphics, ptx, getX() + width - ptxWidth - 3, processStartY - 3);
        processWidth -= ptxWidth + 3;

        OERenderUtils.drawTexture(MusicManagerMonitor.WIDGETS_TEXTURE, guiGraphics.pose(), processStartX, processStartY, 58, 81, processWidth, 3);
        OERenderUtils.drawTexture(MusicManagerMonitor.WIDGETS_TEXTURE, guiGraphics.pose(), processStartX, processStartY, 58, 78, (float) processWidth * process, 3);

        if(lyricGetter != null && lyricGetter.isFinish()){
            lyric = lyricGetter.getLyric();
            lyricGetter = null;
        }

        if(lyric == null)return;
        if(lyric.isEmpty())return;
        var part = lyric.getPart(data.getMusicPosition() / 1000f);
        drawSmartCenterText(guiGraphics, Component.translatable(OEClientUtils.getWidthOmitText(part.getA(),
                        width - 5,
                        "...")),
                getX() + width / 2f, getY() + baseHeight + 1);
        if(part.getB() != null)
            drawSmartCenterText(guiGraphics, Component.translatable(OEClientUtils.getWidthOmitText(part.getB(),
                            width - 5,
                            "...")),
                    getX() + width / 2f, getY() + baseHeight + 2 + mc.font.lineHeight);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {

    }
}
