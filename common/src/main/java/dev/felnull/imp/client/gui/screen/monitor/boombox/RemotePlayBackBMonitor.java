package dev.felnull.imp.client.gui.screen.monitor.boombox;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.block.BoomboxData;
import dev.felnull.imp.client.gui.components.*;
import dev.felnull.imp.client.gui.screen.BoomboxScreen;
import dev.felnull.imp.client.gui.screen.monitor.music_manager.MusicManagerMonitor;
import dev.felnull.imp.music.resource.ImageInfo;
import dev.felnull.imp.music.resource.Music;
import dev.felnull.imp.music.resource.MusicSource;
import org.modsauce.otyacraftenginerenewed.client.util.OERenderUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class RemotePlayBackBMonitor extends PlayBackFiniteBaseBMMonitor {
    private static final ResourceLocation BACK_BG_TEXTURE = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "textures/gui/container/boombox/monitor/playback_back.png");
    private SmartButton backButton;
    /** The green text toggle below: auto play / stop after this (only whether to continue; which track follows is decided by the loop button's three states). */
    private AutoPlayWidget autoPlayWidget;
    /** The "click/double click" hit area laid over the track name row (draws nothing, see {@link DoubleClickArea}). */
    private DoubleClickArea nameDoubleClickArea;

    public RemotePlayBackBMonitor(BoomboxData.MonitorType monitorType, BoomboxScreen screen) {
        super(monitorType, screen);
    }

    @Override
    public void init(int leftPos, int topPos) {
        super.init(leftPos, topPos);
        this.backButton = this.addRenderWidget(new SmartButton(getStartX() + width - 15, getStartY() + height - 12, 14, 11, Component.translatable("gui.back"), n -> setMonitor(BoomboxData.MonitorType.REMOTE_PLAYBACK_SELECT)));
        this.backButton.setHideText(true);
        this.backButton.setIcon(MusicManagerMonitor.WIDGETS_TEXTURE, 11, 123, 8, 8);

        // The green text below = "whether to continue": auto play / stop after this, at (x≈148, y=16).
        // It is independent of the loop button's three states above: that one decides "which track follows",
        // this one decides "whether to continue".
        this.autoPlayWidget = this.addRenderWidget(new AutoPlayWidget(getStartX() + 100, getStartY() + 16, 40,
                () -> getScreen().isAutoPlay(),
                () -> getScreen().insAutoPlay(!getScreen().isAutoPlay())));
        this.autoPlayWidget.visible = canPlay();

        // [Double click the track name → jump to this track's position in the playlist]
        // The name is **drawn text** (see PlayBackBaseBMonitor#render), not a widget ⇒ a click area that
        // "draws nothing" (DoubleClickArea) is laid over it, accepting double clicks only; appearance untouched.
        this.nameDoubleClickArea = this.addRenderWidget(new DoubleClickArea(
                getStartX() + 2, getStartY() + 3, width - 4, 9,
                Component.translatable("imp.text.boomboxJumpToPlayList"),
                this::jumpToPlayListPosition));
    }

    /**
     * Keeps the "double click the track name" hit area on the name row; recomputed each tick because the name start
     * depends on whether the (asynchronously loaded) cover is there.
     */
    private void syncNameClickArea() {
        if (nameDoubleClickArea == null)
            return;
        int sx = 2 + (getPlayBackImage().isEmpty() ? 0 : height - 2);
        nameDoubleClickArea.setX(getStartX() + sx);
        nameDoubleClickArea.setWidth(Math.max(1, width - sx - 2));
    }

    /** [name double click] Navigate to this track in the playlist — never {@code insSelectedMusic} (it replaces the playing track). */
    private void jumpToPlayListPosition() {
        var data = getScreen().getBoomBoxData();
        var m = data.getSelectedMusic();
        if (m == null)
            return;
        var pl = resolvePlayListOf(data, m.getUuid());
        if (pl == null)
            return;
        getScreen().setFocusMusic(m.getUuid(), pl);
        getScreen().insSelectedPlayList(pl);
        setMonitor(BoomboxData.MonitorType.REMOTE_PLAYBACK_SELECT);
    }

    /**
     * [Which playlist this track is actually in] Must not be fudged with "the currently selected playlist".
     */
    @Nullable
    private UUID resolvePlayListOf(BoomboxData data, UUID musicId) {
        var selected = data.getSelectedPlayList(mc.player);
        if (selected != null && cachedContains(selected, musicId))
            return selected;
        for (var e : getSyncManager().musics.entrySet())
            if (cachedContains(e.getKey(), musicId))
                return e.getKey();
        return selected;
    }

    /** Whether this track is in the **cached** list of this playlist (no `getMusics` call, to avoid sync packets while browsing). */
    private boolean cachedContains(UUID playListId, UUID musicId) {
        var list = getSyncManager().musics.get(playListId);
        if (list == null)
            return false;
        for (Music m : list)
            if (musicId.equals(m.getUuid()))
                return true;
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        syncNameClickArea();

        // [What if it cannot play] The check cannot live here — this tick does not run while the UI is off this page. The server handles it from the client's reported load result, independent of any UI.
    }

    @Override
    public void render(GuiGraphics guiGraphics, float f, int mouseX, int mouseY) {
        super.render(guiGraphics, f, mouseX, mouseY);
        OERenderUtils.drawTexture(BACK_BG_TEXTURE, guiGraphics.pose(), getStartX(), getStartY(), 0f, 0f, width, height, width, height);

        // [Load failure hint] Drawn after the background (otherwise the background covers it). **One short line only**, do not smear the UI.
        var m = getScreen().getBoomBoxData().getSelectedMusic();
        if (m != null && m.getSource() != null
                && dev.felnull.imp.client.music.MusicLoadFailure.isFailed(m.getSource().getIdentifier())) {
            drawSmartCenterText(guiGraphics, failHintText(m.getSource().getLoaderType(), m.getSource().getIdentifier()),
                    getStartX() + width / 2f, getStartY() + height - 10, 0xFFFF5555);
        }
    }

    /**
     * Pick a hint sentence (give the reason when it "cannot play").
     */
    private static net.minecraft.network.chat.Component failHintText(String loaderType, @org.jetbrains.annotations.Nullable String identifier) {
        var reason = dev.felnull.imp.client.music.MusicLoadFailure.reason(identifier);
        if (reason != null && !reason.isBlank())
            return net.minecraft.network.chat.Component.translatable("imp.text.playFail.reason", reason);
        var key = switch (loaderType == null ? "" : loaderType) {
            case "qq_music" -> "imp.text.playFail.qq";
            case "kugou" -> "imp.text.playFail.kugou";
            case "netease" -> "imp.text.playFail.netease";
            default -> "imp.text.playFail.other";
        };
        return net.minecraft.network.chat.Component.translatable(key);
    }

    @Override
    public void renderAppearance(PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int j, float f, float monitorWidth, float monitorHeight, BoomboxData data) {
        super.renderAppearance(poseStack, multiBufferSource, i, j, f, monitorWidth, monitorHeight, data);
        OERenderUtils.renderTextureSprite(BACK_BG_TEXTURE, poseStack, multiBufferSource, 0, 0, OERenderUtils.MIN_BREADTH * 2, 0, 0, 0, monitorWidth, monitorHeight, 0, 0, width, height, width, height, i, j);
        float onPxW = monitorWidth / (float) width;
        float onPxH = monitorHeight / (float) height;


        renderSmartButtonSprite(poseStack, multiBufferSource, width - 15, height - 12, OERenderUtils.MIN_BREADTH * 2f, 14, 11, i, j, onPxW, onPxH, monitorHeight, MusicManagerMonitor.WIDGETS_TEXTURE, 11, 123, 8, 8, 256, 256);

        // Same convention as the main UI: this shows "auto play / stop after this" (whether to continue)
        renderSmartCenterTextSprite(poseStack, multiBufferSource,
                Component.translatable(data.isAutoPlay() ? "imp.text.autoPlay" : "imp.text.autoPlay.stop"),
                120, 16, OERenderUtils.MIN_BREADTH * 2f, onPxW, onPxH, monitorHeight, i, 0XFF115D0E);
    }

    @Override
    protected @NotNull ImageInfo getPlayBackImage(BoomboxData data) {
        var m = getMusic(data);
        if (m != null) return m.getImage();
        return ImageInfo.EMPTY;
    }

    @Override
    protected @NotNull String getPlayBackName(BoomboxData data) {
        var m = getMusic(data);
        if (m != null) return m.getName();
        return "";
    }

    @Override
    protected @NotNull String getPlayBackAuthor(BoomboxData data) {
        var m = getMusic(data);
        if (m != null) return m.getAuthor();
        return "";
    }

    @Override
    protected @NotNull MusicSource getPlayBackSource(BoomboxData data) {
        var m = getMusic(data);
        if (m != null) return m.getSource();
        return MusicSource.EMPTY;
    }

    private Music getMusic() {
        return getScreen().getBoomBoxData().getSelectedMusic();
    }

    private Music getMusic(BoomboxData data) {
        return data.getSelectedMusic();
    }

    @Override
    protected boolean isShortTipProgressBar() {
        return true;
    }
}
