package dev.felnull.imp.client.gui.screen.monitor.music_manager;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.felnull.imp.blockentity.MusicManagerBlockEntity;
import dev.felnull.imp.client.gui.components.SmartButton;
import dev.felnull.imp.client.gui.screen.MusicManagerScreen;
import dev.felnull.imp.client.music.link.LinkDebug;
import dev.felnull.imp.client.music.playlist.IMPPlaylistLoaders;
import dev.felnull.imp.client.music.playlist.IPlaylistLoader;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import org.modsauce.otyacraftenginerenewed.client.util.OERenderUtils;

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

        // List only "platform main loaders": the other loaders of the same platform (NetEase radio / single episode) do not get their own buttons.
        // They still take part in the clipboard recognition below, so pasting the matching link lands precisely on the matching import page.
        List<IPlaylistLoader> loaders = visibleLoaders();
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

            // [Performance] Resolve once for the whole clipboard with identifier conversion off (no network): per-loader resolving would repeat the work and freeze the client thread.
            String hintLoaderId = IPlaylistLoader.playlistLoaderIdFromClipboard(clipboard);
            LinkDebug.log("import-select clip-hint", "loaderId", hintLoaderId, "clipboardLen", clipboard.length());

            for (IPlaylistLoader loader: loaders){
                var r = loader.autoPasteFromClipboard(clipboard);
                // When the loader's own recognition does not hit, platform-independent share link recognition runs (NetEase playlist/album/radio, QQ Music playlist, Bilibili favorites)
                // —— only on the loader identified by the whole-clipboard resolve above.
                if (r.isEmpty() && hintLoaderId != null && hintLoaderId.equals(loader.getID()))
                    r = IPlaylistLoader.autoPasteFromMusicLink(loader, clipboard, false);
                if(r.isPresent()){
                    // The same clipboard content auto-jumps only once, otherwise going back to the import page bounces straight back here ("back does not work"). Clicking a platform button yourself is unaffected.
                    if (loader.getID().equals(lastAutoJumpLoader) && clipboard.equals(lastAutoJumpClipboard)) {
                        LinkDebug.log("import-select auto-jump SKIP-SAME", "loader", loader.getID(), "clipboardLen", clipboard.length());
                        return;
                    }
                    lastAutoJumpLoader = loader.getID();
                    lastAutoJumpClipboard = clipboard;
                    LinkDebug.log("import-select auto-jump", "loader", loader.getID(), "clipboardLen", clipboard.length());
                    getScreen().insImportPlayListType(loader.getID());
                    insMonitor(resolveMonitor());
                    return;
                }
            }
        }
    }

    /** Last "clipboard auto jump" (importer id + clipboard text), **static** on purpose: it must survive going back. */
    private static String lastAutoJumpLoader;
    private static String lastAutoJumpClipboard;

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

        List<IPlaylistLoader> loaders = visibleLoaders();
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

    /** The importers to list on the selection page (hiding same-platform radio / single episode etc., see {@link IPlaylistLoader#showInSelectScreen()}). */
    private static List<IPlaylistLoader> visibleLoaders() {
        return IMPPlaylistLoaders.getAllLoaders().stream()
                .filter(IPlaylistLoader::showInSelectScreen)
                .toList();
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