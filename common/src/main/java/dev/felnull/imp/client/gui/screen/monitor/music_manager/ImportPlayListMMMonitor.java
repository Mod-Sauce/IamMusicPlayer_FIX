package dev.felnull.imp.client.gui.screen.monitor.music_manager;

import dev.felnull.imp.blockentity.MusicManagerBlockEntity;
import dev.felnull.imp.client.bilibili.BiliBiliSpaceUtil;
import dev.felnull.imp.client.gui.screen.MusicManagerScreen;
import dev.felnull.imp.client.music.playlist.IPlaylistResolver;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

public class ImportPlayListMMMonitor extends ImportPlayListBaseMMMonitor{
    protected static final Component IMPORTING_TEXT = Component.translatable("imp.text.importing");
    protected static final Component IMPORT_FAILURE_TEXT = Component.translatable("imp.text.importFailure");
    private IPlaylistResolver importMusicLoaderThread;
    public ImportPlayListMMMonitor(MusicManagerBlockEntity.MonitorType type, MusicManagerScreen screen) {
        super(type, screen);
    }

    @Override
    protected void onImport() {
        if (isImporting()) return;
        var ipl = getImportPlayList();
        if (getImportPlayListMusicCount() > 0 && !ipl.isEmpty())
            startImportMusicLoader(ipl);
    }

    @Override
    protected MusicManagerBlockEntity.MonitorType fallbackParentType() {
        // Fallback for "origin unknown" only: overriding getParentType() would shadow the
        // base "return to the real origin" branch.
        return MusicManagerBlockEntity.MonitorType.ADD_ONLINE_PLAY_LIST;
    }

    @Override
    public void render(GuiGraphics guiGraphics, float f, int mouseX, int mouseY) {
        super.render(guiGraphics, f, mouseX, mouseY);
        Component ipTx = null;
        if (isImporting()) {
            ipTx = IMPORTING_TEXT;
        } else if (importMusicLoaderThread != null && importMusicLoaderThread.isFailure()) {
            ipTx = IMPORT_FAILURE_TEXT;
        } else if (importRefused()) {
            ipTx = importRefusedStatus();
        } else if (importSending()) {
            ipTx = Component.translatable("imp.text.importProgress", importBatchSent(), importBatchTotal());
        } else if (getImportPlayListMusicCount() > 0) {
            ipTx = Component.translatable("imp.text.importMusicCount", getImportPlayListMusicCount());
        }
        if (ipTx != null)
            drawSmartText(guiGraphics, ipTx, getStartX() + width - 95 + 7, getStartY() + 184);
    }

    private boolean isImporting() {
        return importMusicLoaderThread != null && importMusicLoaderThread.isAlive();
    }

    private void startImportMusicLoader(String id) {
        stopImportMusicLoader();
        // ⚠️ Not getPlaylistLoader().getResolver(id) directly: in "UP 主页" mode the videos are already loaded and
        // the resolver must not walk the space a second time (see ImportPlayListBaseMMMonitor#createImportResolver).
        importMusicLoaderThread = createImportResolver(id);
        importMusicLoaderThread.start();
    }

    private void stopImportMusicLoader() {
        if (importMusicLoaderThread != null) {
            importMusicLoaderThread.stopped();
            importMusicLoaderThread = null;
        }
    }

    @Override
    protected void resetImport() {
        super.resetImport();
        stopImportMusicLoader();
    }

    @Override
    public void tick() {
        super.tick();
        if(importMusicLoaderThread == null)return;
        if(isImporting())return;
        if(importMusicLoaderThread.isFailure())return;
        // The list is sent as batches (one packet cannot carry it): the first creates the playlist, this page leaves
        // once the last one is out (see ImportPlayListBaseMMMonitor#startChunkedImport).
        var source = importSource();
        var musics = List.copyOf(getMusics());
        importMusicLoaderThread = null;
        startChunkedImport(musics, UUID.randomUUID(), true, source);
    }

    /** Every batch is out: back to the playlist page (its songs arrive page by page — see MusicSyncManager). */
    @Override
    protected void importFinished(UUID playListId) {
        insMonitor(MusicManagerBlockEntity.MonitorType.PLAY_LIST);
    }

    /**
     * The 歌单来源 the import creates the playlist with ({@code space:<mid>} / {@code fav:<fid>} / empty); 「刷新」 works
     * from it. **Only** the B站 loader may set it — another platform's numeric id reads as a B站 favourites folder.
     */
    private String importSource() {
        var loader = getPlaylistLoader();
        if (loader == null || !"bilibili".equals(loader.getID()))
            return "";
        // 用**解析结果**的 id（`space:<mid>` / 裸 fid），别用输入框原文：粘链接时 `sourceOf(原文)` 是空串，
        // 那个歌单就没有来源、永远不出现「刷新」键。
        var res = importMusicLoaderThread == null ? null : importMusicLoaderThread.getResult();
        var id = res == null ? null : res.id();
        return BiliBiliSpaceUtil.sourceOf(id == null || id.isBlank() ? getImportPlayList() : id);
    }
}
