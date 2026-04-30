package dev.felnull.imp.client.gui.screen.monitor.music_manager;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.blockentity.MusicManagerBlockEntity;
import dev.felnull.imp.client.gui.components.SmartButton;
import dev.felnull.imp.client.gui.screen.MusicManagerScreen;
import org.modsauce.otyacraftenginerenewed.client.util.OERenderUtils;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;

public class AddOnlinePlayListMMMonitor extends MusicManagerMonitor {
    private static final Component IMPORT_YOUTUBE_PLAYLIST_TEXT = Component.translatable("imp.button.importYoutubePlayList");
    private static final Component IMPORT_NETEASE_PLAYLIST_TEXT = Component.translatable("imp.button.importNeteasePlayList");

    public AddOnlinePlayListMMMonitor(MusicManagerBlockEntity.MonitorType type, MusicManagerScreen screen) {
        super(type, screen);
    }

    @Override
    public void init(int leftPos, int topPos) {
        super.init(leftPos, topPos);
        var button = new SmartButton(getStartX() + (width - 270) / 2, getStartY() + (height - 15) / 2 + 15, 270, 15, IMPORT_YOUTUBE_PLAYLIST_TEXT, n -> insMonitor(MusicManagerBlockEntity.MonitorType.IMPORT_YOUTUBE_PLAY_LIST));
        button.active = false; // todo: YouTube import has two implementations, but it is not implemented here.
        addRenderWidget(button);
        button = new SmartButton(getStartX() + (width - 270) / 2, getStartY() + (height - 15) / 2 - 15, 270, 15, IMPORT_NETEASE_PLAYLIST_TEXT, n -> insMonitor(MusicManagerBlockEntity.MonitorType.IMPORT_NETEASE_PLAY_LIST));
        button.active = IamMusicPlayer.getConfig().netMusicConfig.enableNetease;
        addRenderWidget(button);
    }

    @Override
    public void renderAppearance(MusicManagerBlockEntity blockEntity, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int j, float f, float monitorWidth, float monitorHeight) {
        super.renderAppearance(blockEntity, poseStack, multiBufferSource, i, j, f, monitorWidth, monitorHeight);
        float onPxW = monitorWidth / (float) width;
        float onPxH = monitorHeight / (float) height;
        renderSmartButtonSprite(poseStack, multiBufferSource, (width - 270f) / 2f, (height - 15f) / 2f + 15, OERenderUtils.MIN_BREADTH * 2, 270, 15, i, j, onPxW, onPxH, monitorHeight, IMPORT_YOUTUBE_PLAYLIST_TEXT, true);
        renderSmartButtonSprite(poseStack, multiBufferSource, (width - 270f) / 2f, (height - 15f) / 2f - 15, OERenderUtils.MIN_BREADTH * 2, 270, 15, i, j, onPxW, onPxH, monitorHeight, IMPORT_NETEASE_PLAYLIST_TEXT, true);
    }

    @Override
    protected MusicManagerBlockEntity.MonitorType getParentType() {
        return MusicManagerBlockEntity.MonitorType.ADD_PLAY_LIST;
    }
}
