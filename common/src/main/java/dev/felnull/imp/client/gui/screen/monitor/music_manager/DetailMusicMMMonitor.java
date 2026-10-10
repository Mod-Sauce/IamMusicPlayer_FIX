package dev.felnull.imp.client.gui.screen.monitor.music_manager;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.felnull.imp.blockentity.MusicManagerBlockEntity;
import dev.felnull.imp.client.gui.EditBoxUtil;
import dev.felnull.imp.client.gui.components.ImageSetButton;
import dev.felnull.imp.client.gui.components.MusicsManageFixedListWidget;
import dev.felnull.imp.client.gui.components.SmartButton;
import dev.felnull.imp.client.gui.screen.MusicManagerScreen;
import dev.felnull.imp.client.music.media.IMPMusicMedias;
import dev.felnull.imp.music.resource.ImageInfo;
import dev.felnull.imp.music.resource.Music;
import dev.felnull.imp.music.resource.MusicPlayList;
import dev.felnull.imp.music.resource.MusicSource;
import org.modsauce.otyacraftenginerenewed.client.util.OERenderUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class DetailMusicMMMonitor extends MusicBaseMMMonitor {
    private static final Component EDIT_TEXT = Component.translatable("imp.button.edit");
    private static final Component DELETE_TEXT = Component.translatable("imp.button.delete").withStyle(ChatFormatting.DARK_RED);
    /** 「分P」: only meaningful for a B站 video (visibility is decided per tick), and the label carries the cached part count. */
    private static final Component PARTS_TEXT = Component.translatable("imp.button.bilibiliParts");
    private static final int PARTS_BUTTON_X = 189;
    private static final int PARTS_BUTTON_Y = 90;
    private static final int PARTS_BUTTON_W = 176;
    private static final int PARTS_BUTTON_H = 15;
    private SmartButton editButton;
    private SmartButton deleteButton;
    private SmartButton partsButton;
    /** Last label given to {@link #partsButton} (the part count in it changes once a lookup lands). */
    private Component partsTextCache;
    private String cashName;

    public DetailMusicMMMonitor(MusicManagerBlockEntity.MonitorType type, MusicManagerScreen screen) {
        super(type, screen);
        locked();
    }

    @Override
    public void init(int leftPos, int topPos) {
        super.init(leftPos, topPos);
        this.editButton = this.addRenderWidget(new SmartButton(getStartX() + width - 95 - 87, getStartY() + 180, 87, 15, EDIT_TEXT, n -> insMonitor(MusicManagerBlockEntity.MonitorType.EDIT_MUSIC)));
        this.editButton.visible = canEdit();

        this.deleteButton = this.addRenderWidget(new SmartButton(getStartX() + width - 5 - 87, getStartY() + 180, 87, 15, DELETE_TEXT, n -> insMonitor(MusicManagerBlockEntity.MonitorType.DELETE_MUSIC)));
        this.deleteButton.visible = canDelete();

        // 「分P」 (B站 videos only): opens the part list, where one part can be previewed on its own —— the parts are
        // **not** playlist entries (see BiliBiliParts), so this is the only way to listen to P3 without playing
        // through from P1. Sits in the right column under the playback controls, which is empty on this page.
        this.partsButton = this.addRenderWidget(new SmartButton(getStartX() + PARTS_BUTTON_X, getStartY() + PARTS_BUTTON_Y,
                PARTS_BUTTON_W, PARTS_BUTTON_H, PARTS_TEXT, n -> insMonitor(MusicManagerBlockEntity.MonitorType.BILIBILI_PARTS)));
        this.partsButton.visible = isBiliBili(getSelectedMusic());
        this.partsTextCache = null;

        this.cashName = getName();
    }

    @Override
    public void renderAppearance(MusicManagerBlockEntity blockEntity, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int j, float f, float monitorWidth, float monitorHeight) {
        super.renderAppearance(blockEntity, poseStack, multiBufferSource, i, j, f, monitorWidth, monitorHeight);
        float onPxW = monitorWidth / (float) width;
        float onPxH = monitorHeight / (float) height;
        if (canEdit(blockEntity))
            renderSmartButtonSprite(poseStack, multiBufferSource, width - 95 - 87, 180, OERenderUtils.MIN_BREADTH * 2, 87, 15, i, j, onPxW, onPxH, monitorHeight, EDIT_TEXT, true);

        if (canDelete(blockEntity))
            renderSmartButtonSprite(poseStack, multiBufferSource, width - 5 - 87, 180, OERenderUtils.MIN_BREADTH * 2, 87, 15, i, j, onPxW, onPxH, monitorHeight, DELETE_TEXT, true);

        // 「分P」: the world panel must draw it itself (widgets only exist on the GUI path)
        var panelMusic = getSelectedMusic(blockEntity);
        if (isBiliBili(panelMusic))
            renderSmartButtonSprite(poseStack, multiBufferSource, PARTS_BUTTON_X, PARTS_BUTTON_Y, OERenderUtils.MIN_BREADTH * 2,
                    PARTS_BUTTON_W, PARTS_BUTTON_H, i, j, onPxW, onPxH, monitorHeight, partsText(panelMusic), true);
    }

    /** Whether a music is a B站 video — the only kind that has parts. */
    private static boolean isBiliBili(@Nullable Music music) {
        return music != null && music.getSource() != null
                && IMPMusicMedias.BILIBILI.getName().equals(music.getSource().getLoaderType());
    }

    /** The label of the 「分P」 key: the part count is appended once it is in the local cache. */
    private static Component partsText(@Nullable Music music) {
        var badge = music == null ? "" : MusicsManageFixedListWidget.partBadge(music);
        return badge.isEmpty() ? PARTS_TEXT : Component.translatable("imp.button.bilibiliPartsBadge", badge);
    }

    @Override
    public boolean done(ImageInfo imageInfo, String name) {
        setSelectedMusic(null);
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        // A guest video (opened from a description link) is not a playlist entry: everything that writes (编辑/删除 keys,
        // cover buttons, image URL box, the name box) targets the **selected song**, so it stays out of reach.
        var guest = guest();
        this.editButton.visible = guest == null && canEdit();
        this.deleteButton.visible = guest == null && canDelete();
        for (var widget : this.renderables) {
            if (widget instanceof ImageSetButton || (widget instanceof EditBox && widget != this.nameEditBox))
                ((AbstractWidget) widget).visible = guest == null;
        }

        // The 「分P」 key follows the selection, and its label shows the part count once that is known
        var music = guest != null ? guest : getSelectedMusic();
        this.partsButton.visible = isBiliBili(music);
        var text = partsText(music);
        if (!text.equals(this.partsTextCache)) {
            this.partsTextCache = text;
            this.partsButton.setMessage(text);
        }

        if (guest != null) {
            // A guest's title is never written back: the name box shows it, read-only
            this.nameEditBox.setEditable(false);
            this.nameEditBox.setResponder(s -> { });
            if (!this.cashName.equals(guest.getName())) {
                this.cashName = guest.getName();
                EditBoxUtil.setValueShowHead(this.nameEditBox, this.cashName);
            }
        } else {
            this.nameEditBox.setEditable(true);
            this.nameEditBox.setResponder(this::setName);
            if (!this.cashName.equals(getName())) {
                this.cashName = getName();
                // Goes through setValueShowHead: a bare setValue would scroll the view to the cursor (the end) and a leftover displayPos
                // can make the name start in the middle —— see EditBoxUtil
                EditBoxUtil.setValueShowHead(this.nameEditBox, this.cashName);
            }
        }
    }

    @Override
    protected @Nullable DoneType getDoneType() {
        return null;
    }

    private boolean canEdit(MusicManagerBlockEntity musicManagerBlockEntity) {
        var m = getSelectedMusic(musicManagerBlockEntity);
        return m != null && m.getOwner().equals(mc.player.getGameProfile().getId());
    }

    private boolean canDelete(MusicManagerBlockEntity musicManagerBlockEntity) {
        var pl = getSelectedMusicPlayList(musicManagerBlockEntity);
        var m = getSelectedMusic(musicManagerBlockEntity);
        return (pl != null && pl.getAuthority().getAuthorityType(mc.player.getGameProfile().getId()).canMusicDelete()) || (m != null && m.getOwner().equals(mc.player.getGameProfile().getId()));
    }


    private boolean canEdit() {
        var m = getSelectedMusic();
        return m != null && m.getOwner().equals(mc.player.getGameProfile().getId());
    }

    private boolean canDelete() {
        var pl = getSelectedMusicPlayList();
        var m = getSelectedMusic();
        return (pl != null && pl.getAuthority().getAuthorityType(mc.player.getGameProfile().getId()).canMusicDelete()) || (m != null && m.getOwner().equals(mc.player.getGameProfile().getId()));
    }

    /** The video opened from a description link, or null (then the page follows the playlist selection). */
    @Nullable
    private Music guest() {
        var screen = getScreen();
        return screen == null ? null : screen.getGuestMusic();
    }

    @Override
    protected @NotNull String getMusicAuthor(@NotNull MusicManagerBlockEntity musicManagerBlockEntity) {
        var g = guest();
        if (g != null)
            return g.getAuthor();
        var m = getSelectedMusic(musicManagerBlockEntity);
        if (m != null)
            return m.getAuthor();
        return "";
    }

    @Override
    protected @Nullable MusicManagerBlockEntity.MonitorType getParentType() {
        return MusicManagerBlockEntity.MonitorType.PLAY_LIST;
    }

    @Override
    protected @NotNull String getMusicLoaderType(@NotNull MusicManagerBlockEntity musicManagerBlockEntity) {
        var g = guest();
        if (g != null)
            return g.getSource().getLoaderType();
        var m = getSelectedMusic(musicManagerBlockEntity);
        if (m != null)
            return m.getSource().getLoaderType();
        return "";
    }

    @Override
    protected @NotNull ImageInfo getImage(MusicManagerBlockEntity musicManagerBlockEntity) {
        var g = guest();
        if (g != null)
            return g.getImage();
        var m = getSelectedMusic(musicManagerBlockEntity);
        if (m != null)
            return m.getImage();
        return ImageInfo.EMPTY;
    }

    @Override
    protected @NotNull String getName(@NotNull MusicManagerBlockEntity musicManagerBlockEntity) {
        var g = guest();
        if (g != null)
            return g.getName();
        var m = getSelectedMusic(musicManagerBlockEntity);
        if (m != null)
            return m.getName();
        return "";
    }

    @Override
    protected @NotNull MusicSource getMusicSource(@NotNull MusicManagerBlockEntity blockEntity) {
        var g = guest();
        if (g != null)
            return g.getSource();
        var m = getSelectedMusic(blockEntity);
        if (m != null)
            return m.getSource();
        return MusicSource.EMPTY;
    }

    private void setSelectedMusic(@Nullable UUID uuid) {
        getScreen().insSelectedMusic(uuid);
    }

    @Nullable
    private Music getSelectedMusic() {
        if (getScreen().getBlockEntity() instanceof MusicManagerBlockEntity musicManagerBlockEntity)
            return getSelectedMusic(musicManagerBlockEntity);
        return null;
    }

    @Nullable
    private Music getSelectedMusic(MusicManagerBlockEntity musicManagerBlockEntity) {
        var id = getSelectedMusicRaw(musicManagerBlockEntity);
        var pl = getSelectedPlayList(musicManagerBlockEntity);
        if (id != null && pl != null) {
            var sms = getSyncManager().getMusics(pl);
            if (sms != null)
                return sms.stream().filter(n -> id.equals(n.getUuid())).findFirst().orElse(null);
        }
        return null;
    }

    @Nullable
    private UUID getSelectedMusicRaw() {
        if (getScreen().getBlockEntity() instanceof MusicManagerBlockEntity musicManagerBlockEntity)
            return getSelectedMusicRaw(musicManagerBlockEntity);
        return null;
    }

    @Nullable
    private UUID getSelectedMusicRaw(MusicManagerBlockEntity musicManagerBlockEntity) {
        return musicManagerBlockEntity.getSelectedMusic(mc.player);
    }

    protected MusicPlayList getSelectedMusicPlayList() {
        if (getScreen().getBlockEntity() instanceof MusicManagerBlockEntity musicManagerBlockEntity)
            return getSelectedMusicPlayList(musicManagerBlockEntity);
        return null;
    }

    protected MusicPlayList getSelectedMusicPlayList(MusicManagerBlockEntity musicManagerBlockEntity) {
        var pls = getSyncManager().getMyPlayList();
        if (pls == null)
            return null;
        return getSyncManager().getMyPlayList().stream().filter(n -> n.getUuid().equals(getSelectedPlayList(musicManagerBlockEntity))).findFirst().orElse(null);
    }

    @Nullable
    protected UUID getSelectedPlayList() {
        if (getScreen().getBlockEntity() instanceof MusicManagerBlockEntity musicManagerBlockEntity)
            return getSelectedPlayList(musicManagerBlockEntity);
        return null;
    }

    @Nullable
    private UUID getSelectedPlayList(MusicManagerBlockEntity musicManagerBlockEntity) {
        return musicManagerBlockEntity.getSelectedPlayList(mc.player);
    }
}
