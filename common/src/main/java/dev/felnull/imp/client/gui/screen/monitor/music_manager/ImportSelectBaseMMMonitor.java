package dev.felnull.imp.client.gui.screen.monitor.music_manager;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.felnull.imp.blockentity.MusicManagerBlockEntity;
import dev.felnull.imp.client.gui.components.SmartButton;
import dev.felnull.imp.client.gui.screen.MusicManagerScreen;
import dev.felnull.imp.client.music.playlist.IMPPlaylistLoaders;
import dev.felnull.imp.client.music.playlist.IPlaylistLoader;
import dev.felnull.otyacraftengine.client.util.OERenderUtils;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;

import java.util.List;

public abstract class ImportSelectBaseMMMonitor extends MusicManagerMonitor {
    private static final int BUTTON_WIDTH = 270;
    private static final int BUTTON_HEIGHT = 15;
    private static final int BUTTON_SPACING = 20;
    private boolean autoTested = false;
    public ImportSelectBaseMMMonitor(MusicManagerBlockEntity.MonitorType type, MusicManagerScreen screen) {
        super(type, screen);
    }

    protected abstract MusicManagerBlockEntity.MonitorType resolveMonitor();

    @Override
    public void init(int leftPos, int topPos) {
        super.init(leftPos, topPos);

        List<IPlaylistLoader> loaders = IMPPlaylistLoaders.getAllLoaders().stream().toList();
        int size = loaders.size();

        int totalHeight = size * BUTTON_HEIGHT + (size - 1) * BUTTON_SPACING;

        for (int i = 0; i < size; i++) {
            IPlaylistLoader loader = loaders.get(i);

            int yOffset = i * (BUTTON_HEIGHT + BUTTON_SPACING) - totalHeight / 2;

            addRenderWidget(new SmartButton(
                    getStartX() + (width - BUTTON_WIDTH) / 2,
                    getStartY() + (height - BUTTON_HEIGHT) / 2 + yOffset,
                    BUTTON_WIDTH,
                    BUTTON_HEIGHT,
                    buildText(loader),
                    n -> {
                        getScreen().insImportPlayListType(loader.getID());
                        insMonitor(resolveMonitor());
                    }
            ));
        }

        autoTested = false;
    }

    @Override
    public void tick() {
        if(!autoTested){
            autoTested = true;
            List<IPlaylistLoader> loaders = IMPPlaylistLoaders.getAllLoaders().stream().toList();
            String clipboard = mc.keyboardHandler.getClipboard();
            for (IPlaylistLoader loader: loaders){
                var r = loader.autoPasteFromClipboard(clipboard);
                if(r.isPresent()){
                    getScreen().insImportPlayListType(loader.getID());
                    insMonitor(resolveMonitor());
                    return;
                }
            }
        }
    }

    @Override
    public void renderAppearance(
            MusicManagerBlockEntity blockEntity,
            PoseStack poseStack,
            MultiBufferSource multiBufferSource,
            int i,
            int j,
            float f,
            float monitorWidth,
            float monitorHeight
    ) {
        super.renderAppearance(blockEntity, poseStack, multiBufferSource, i, j, f, monitorWidth, monitorHeight);

        List<IPlaylistLoader> loaders = IMPPlaylistLoaders.getAllLoaders().stream().toList();
        int size = loaders.size();

        float onPxW = monitorWidth / (float) width;
        float onPxH = monitorHeight / (float) height;

        int totalHeight = size * BUTTON_HEIGHT + (size - 1) * BUTTON_SPACING;

        for (int i2 = 0; i2 < size; i2++) {
            IPlaylistLoader loader = loaders.get(i2);

            int yOffset = i2 * (BUTTON_HEIGHT + BUTTON_SPACING) - totalHeight / 2;

            renderSmartButtonSprite(
                    poseStack,
                    multiBufferSource,
                    (width - BUTTON_WIDTH) / 2f,
                    (height - BUTTON_HEIGHT) / 2f + yOffset,
                    OERenderUtils.MIN_BREADTH * 2,
                    BUTTON_WIDTH,
                    BUTTON_HEIGHT,
                    i,
                    j,
                    onPxW,
                    onPxH,
                    monitorHeight,
                    buildText(loader),
                    true
            );
        }
    }

    protected Component buildText(IPlaylistLoader loader) {
        String id = loader.getID();
        if (id == null || id.isEmpty()) {
            return Component.literal("Unknown");
        }

        String cap = Character.toUpperCase(id.charAt(0)) + id.substring(1);

        return Component.translatable(
                "imp.button.import%sPlayList".formatted(cap)
        );
    }
}