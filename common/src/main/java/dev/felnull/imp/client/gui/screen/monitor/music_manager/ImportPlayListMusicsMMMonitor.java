package dev.felnull.imp.client.gui.screen.monitor.music_manager;

import dev.architectury.networking.NetworkManager;
import dev.felnull.imp.blockentity.MusicManagerBlockEntity;
import dev.felnull.imp.client.gui.screen.MusicManagerScreen;
import dev.felnull.imp.client.music.playlist.IPlaylistResolver;
import dev.felnull.imp.networking.IMPPackets;
import dev.felnull.otyacraftengine.networking.existence.BlockEntityExistence;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class ImportPlayListMusicsMMMonitor extends ImportPlayListBaseMMMonitor{
    protected static final Component IMPORTING_TEXT = Component.translatable("imp.text.importing");
    protected static final Component IMPORT_FAILURE_TEXT = Component.translatable("imp.text.importFailure");
    private IPlaylistResolver importMusicLoaderThread;


    public ImportPlayListMusicsMMMonitor(MusicManagerBlockEntity.MonitorType type, MusicManagerScreen screen) {
        super(type, screen);
    }

    @Override
    public void render(GuiGraphics guiGraphics, float f, int mouseX, int mouseY) {
        super.render(guiGraphics, f, mouseX, mouseY);
        Component ipTx = null;
        if (isImporting()) {
            ipTx = IMPORTING_TEXT;
        } else if (importMusicLoaderThread != null && importMusicLoaderThread.isFailure()) {
            ipTx = IMPORT_FAILURE_TEXT;
        } else if (getImportPlayListMusicCount() > 0) {
            ipTx = Component.translatable("imp.text.importMusicCount", getImportPlayListMusicCount());
        }
        if (ipTx != null)
            drawSmartText(guiGraphics, ipTx, getStartX() + width - 95 + 7, getStartY() + 184);
    }

    @Override
    protected void onImport() {
        if (isImporting()) return;
        var ipl = getImportPlayList();
        if (getImportPlayListMusicCount() > 0 && !ipl.isEmpty())
            startImportMusicLoader(ipl);
    }

    @Override
    protected MusicManagerBlockEntity.MonitorType getParentType() {
        return MusicManagerBlockEntity.MonitorType.IMPORT_MUSICS_SELECT;
    }

    private boolean isImporting() {
        return importMusicLoaderThread != null && importMusicLoaderThread.isAlive();
    }

    private void startImportMusicLoader(String id) {
        stopImportMusicLoader();
        importMusicLoaderThread = getPlaylistLoader().getResolver(id);
        importMusicLoaderThread.start();
    }

    private void stopImportMusicLoader() {
        if (importMusicLoaderThread != null) {
            importMusicLoaderThread.stopped();
            importMusicLoaderThread = null;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if(importMusicLoaderThread == null)return;
        if(isImporting())return;
        if(importMusicLoaderThread.isFailure())return;

        if (getScreen().getBlockEntity() instanceof MusicManagerBlockEntity musicManagerBlock)
            NetworkManager.sendToServer(IMPPackets.MULTIPLE_MUSIC_ADD, new IMPPackets.MultipleMusicAddMessage(musicManagerBlock.getSelectedPlayList(mc.player), getMusics(), BlockEntityExistence.getByBlockEntity(getScreen().getBlockEntity())).toFBB());
        resetImport();
        importMusicLoaderThread = null;
        insMonitor(MusicManagerBlockEntity.MonitorType.PLAY_LIST);
    }
}
